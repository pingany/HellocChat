#!/bin/bash

import os, sys, traceback, threading, asyncore, struct, signal
from messages_pb2 import Message
LOCAL_HOST = "127.0.0.1"

LOOP_TIMEOUT = 1    # seconds, timeout for select operation

RECV_BUF_SIZE = 4096

DEBUG = "NDEBUG" not in os.environ

globals()["__debug__"] = DEBUG

def backtrace ():
	exc_type, exc_value, exc_tb = sys.exc_info()
	traceback.print_exception(exc_type, exc_value, exc_tb, file=sys.stderr)

def write(fn, c):
	f = open(fn, "wb")
	f.write(c)
	f.close()

def read(fn, l=-1):
    f = open(fn, "rb")
    r = f.read(l)
    f.close()
    return r

def normpath(path):
    return path.replace('/', os.path.sep).replace('\\', os.path.sep)

def log(*msgs):
        if DEBUG:
                sys.stderr.write('log: %s\n' % " ".join([str(msg) for msg in msgs]))
		sys.stderr.flush()

def tostr(msg):
    if msg.type == Message.CHAT and msg.chat.type != Message.Chat.TEXT:
        return "Message Chat type : %s, filename = %s, datalen = %d" \
         %  (msg.chat.type, msg.chat.filename, len(msg.chat.data))
    else:
        return str(msg)

class ExitHandler:

    exit_handlers = []

    @staticmethod
    def registerExitHandler(handler):
        ExitHandler.exit_handlers.append(handler)

    @staticmethod
    def unregisterExitHandler(handler):
        ExitHandler.exit_handlers.remove(handler)

    @staticmethod
    def exitHandler(sig, frame):
        log("ExitHandler.exitHandler")
        for h in ExitHandler.exit_handlers:
            h()
    
    @staticmethod
    def setup():
        signal.signal(signal.SIGINT, ExitHandler.exitHandler)
        signal.signal(signal.SIGTERM, ExitHandler.exitHandler)

class HellocConnection(asyncore.dispatcher):

    thread_locker = threading.Lock()
    loop_running = False

    def __init__(self, soc, message_handler, soc_closed=None, soc_addr=None):
        HellocConnection.thread_locker.acquire()
        asyncore.dispatcher.__init__(self, sock=soc)
        self.readbuf = ""
        self.sendbuf = ""
        assert message_handler
        self.message_handler = message_handler
        self.soc_closed_handler = soc_closed
        self.locker = threading.Lock()
        self.running = True
        HellocConnection.thread_locker.release()

    def stop(self):
        log("HellocConnection stop")
        backtrace()
        self.running = False
        self.close()
        if self.soc_closed_handler:
            self.soc_closed_handler()

    def handle_message(self, message):
        log("HellocConnection handle_message", tostr(message))
        self.message_handler(message)

    def handle_connect(self):
        assert False
        pass

    def log_info(self, message, type='info'):
        log('%s: %s' % (type, message))

    def send_message(self, message):
        #log("HellocConnection send_message", message)
        bytes = message.SerializeToString()
        msglen = len(bytes)

        self.locker.acquire()
        self.sendbuf += struct.pack(">L", msglen)
        self.sendbuf += bytes
        self.locker.release()

    def handle_close(self):
        log("HellocConnection handle_close")
        self.stop()

    def handle_read(self):
        self.readbuf += self.recv(RECV_BUF_SIZE)
        log("HellocConnection handle_read, data len:", len(self.readbuf))

        while len(self.readbuf) > 4:
            r = len(self.readbuf)
            msglen = struct.unpack(">L", self.readbuf[:4])[0]
            if r - 4 >= msglen:
                msg = Message()
                msg.ParseFromString(self.readbuf[4: 4 + msglen])
                self.readbuf = self.readbuf[4 + msglen :]
                self.handle_message(msg)
            else:
                break

    def writable(self):
        self.locker.acquire()
        ret = len(self.sendbuf) > 0
        self.locker.release()
        return ret

    def handle_write(self):
        sent = self.send(self.sendbuf)
        log("HellocConnection handle_write, sent = ", sent)
        self.locker.acquire()
        self.sendbuf = self.sendbuf[sent:]
        self.locker.release()
