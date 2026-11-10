#include "rfcomm_server.hpp"

#include <iostream>
#include <string>
#include <vector>

#ifdef _WIN32

#include <winsock2.h>
#include <ws2bth.h>
#include <bluetoothapis.h>
#include <cstdio>

#include "ipc_pipe.hpp"

namespace airinput::win {
namespace {

GUID parseUuid(const std::wstring& text) {
  GUID guid{};
  unsigned long d1 = 0;
  unsigned short d2 = 0;
  unsigned short d3 = 0;
  unsigned int b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0, b7 = 0;
  if (swscanf_s(
          text.c_str(),
          L"%8lx-%4hx-%4hx-%2x%2x-%2x%2x%2x%2x%2x%2x",
          &d1,
          &d2,
          &d3,
          &b0,
          &b1,
          &b2,
          &b3,
          &b4,
          &b5,
          &b6,
          &b7) < 11) {
    return guid;
  }
  guid.Data1 = d1;
  guid.Data2 = d2;
  guid.Data3 = d3;
  guid.Data4[0] = static_cast<unsigned char>(b0);
  guid.Data4[1] = static_cast<unsigned char>(b1);
  guid.Data4[2] = static_cast<unsigned char>(b2);
  guid.Data4[3] = static_cast<unsigned char>(b3);
  guid.Data4[4] = static_cast<unsigned char>(b4);
  guid.Data4[5] = static_cast<unsigned char>(b5);
  guid.Data4[6] = static_cast<unsigned char>(b6);
  guid.Data4[7] = static_cast<unsigned char>(b7);
  return guid;
}

struct Runtime {
  SOCKET listenSock = INVALID_SOCKET;
  SOCKET clientSock = INVALID_SOCKET;
  WSAQUERYSETW qs{};
  CSADDR_INFO addrInfo{};
  SOCKADDR_BTH localAddr{};
  GUID serviceGuid{};
  IpcPipe control;
  IpcPipe audio;
  uint64_t packets = 0;
};

Runtime* g_rt = nullptr;

}  // namespace

RfcommServer::RfcommServer(std::wstring serviceUuid) : serviceUuid_(std::move(serviceUuid)) {}

bool RfcommServer::start() {
  WSADATA wsa{};
  if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
    std::cerr << "WSAStartup failed\n";
    return false;
  }

  auto* rt = new Runtime();
  g_rt = rt;
  rt->serviceGuid = parseUuid(serviceUuid_);

  rt->listenSock = socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
  if (rt->listenSock == INVALID_SOCKET) {
    std::cerr << "AF_BTH socket failed: " << WSAGetLastError() << "\n";
    return false;
  }

  memset(&rt->localAddr, 0, sizeof(rt->localAddr));
  rt->localAddr.addressFamily = AF_BTH;
  rt->localAddr.btAddr = 0;
  rt->localAddr.port = BT_PORT_ANY;

  if (bind(rt->listenSock, reinterpret_cast<SOCKADDR*>(&rt->localAddr), sizeof(rt->localAddr)) ==
      SOCKET_ERROR) {
    std::cerr << "bind failed: " << WSAGetLastError() << "\n";
    return false;
  }

  int namelen = sizeof(rt->localAddr);
  if (getsockname(rt->listenSock, reinterpret_cast<SOCKADDR*>(&rt->localAddr), &namelen) ==
      SOCKET_ERROR) {
    std::cerr << "getsockname failed\n";
    return false;
  }

  memset(&rt->qs, 0, sizeof(rt->qs));
  memset(&rt->addrInfo, 0, sizeof(rt->addrInfo));
  rt->addrInfo.LocalAddr.lpSockaddr = reinterpret_cast<LPSOCKADDR>(&rt->localAddr);
  rt->addrInfo.LocalAddr.iSockaddrLength = sizeof(SOCKADDR_BTH);
  rt->addrInfo.RemoteAddr.lpSockaddr = reinterpret_cast<LPSOCKADDR>(&rt->localAddr);
  rt->addrInfo.RemoteAddr.iSockaddrLength = sizeof(SOCKADDR_BTH);
  rt->addrInfo.iSocketType = SOCK_STREAM;
  rt->addrInfo.iProtocol = BTHPROTO_RFCOMM;

  static wchar_t name[] = L"AirInput Audio";
  rt->qs.dwSize = sizeof(WSAQUERYSETW);
  rt->qs.lpszServiceInstanceName = name;
  rt->qs.lpServiceClassId = &rt->serviceGuid;
  rt->qs.dwNameSpace = NS_BTH;
  rt->qs.dwNumberOfCsAddrs = 1;
  rt->qs.lpcsaBuffer = &rt->addrInfo;

  if (WSASetServiceW(&rt->qs, RNRSERVICE_REGISTER, 0) != 0) {
    std::cerr << "WSASetService REGISTER failed: " << WSAGetLastError() << "\n";
    return false;
  }

  if (listen(rt->listenSock, 1) == SOCKET_ERROR) {
    std::cerr << "listen failed\n";
    return false;
  }

  if (!rt->control.create(L"\\\\.\\pipe\\airinput-control", false) ||
      !rt->audio.create(L"\\\\.\\pipe\\airinput-audio", true)) {
    std::cerr << "named pipe create failed\n";
    return false;
  }

  running_ = true;
  std::cout << "{\"state\":\"listening\",\"channel\":" << rt->localAddr.port << "}\n";
  std::cout.flush();
  return true;
}

void RfcommServer::runControlLoop() {
  auto* rt = g_rt;
  if (!rt) return;

  while (running_) {
    fd_set readfds;
    FD_ZERO(&readfds);
    FD_SET(rt->listenSock, &readfds);
    timeval tv{1, 0};
    const int sel = select(0, &readfds, nullptr, nullptr, &tv);
    if (sel > 0 && FD_ISSET(rt->listenSock, &readfds)) {
      SOCKADDR_BTH clientAddr{};
      int len = sizeof(clientAddr);
      SOCKET client = accept(rt->listenSock, reinterpret_cast<SOCKADDR*>(&clientAddr), &len);
      if (client != INVALID_SOCKET) {
        rt->clientSock = client;
        std::cout << "{\"state\":\"connected\"}\n";
        std::cout.flush();
        char buf[4096];
        for (;;) {
          const int n = recv(client, buf, sizeof(buf), 0);
          if (n <= 0) break;
          rt->packets += 1;
          rt->audio.writeBinary(reinterpret_cast<const uint8_t*>(buf), static_cast<size_t>(n));
          if (rt->packets == 1 || (rt->packets % 50) == 0) {
            std::cout << "{\"state\":\"streaming\",\"packets\":" << rt->packets << "}\n";
            std::cout.flush();
          }
        }
        closesocket(client);
        rt->clientSock = INVALID_SOCKET;
        std::cout << "{\"state\":\"listening\"}\n";
        std::cout.flush();
      }
    }

    // Non-blocking stdin poll via PeekNamedPipe is not applicable; use line reads with timeout
    // by checking if a line is available — for console apps, users send {"cmd":"stop"}.
    if (std::cin.rdbuf()->in_avail() > 0) {
      std::string line;
      std::getline(std::cin, line);
      if (line.find("stop") != std::string::npos) {
        running_ = false;
      }
    }
  }
}

void RfcommServer::stop() {
  running_ = false;
  auto* rt = g_rt;
  if (!rt) return;
  if (rt->clientSock != INVALID_SOCKET) closesocket(rt->clientSock);
  if (rt->listenSock != INVALID_SOCKET) {
    WSASetServiceW(&rt->qs, RNRSERVICE_DELETE, 0);
    closesocket(rt->listenSock);
  }
  rt->control.close();
  rt->audio.close();
  delete rt;
  g_rt = nullptr;
  WSACleanup();
}

}  // namespace airinput::win

#else

namespace airinput::win {
RfcommServer::RfcommServer(std::wstring) {}
bool RfcommServer::start() { return false; }
void RfcommServer::runControlLoop() {}
void RfcommServer::stop() {}
}  // namespace airinput::win

#endif
