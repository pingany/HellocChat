#!/bin/bash

import socket,sys,re,os,threading,traceback, time, asyncore
from optparse import OptionParser

from Utils import Message
from Utils import LOCAL_HOST, LOOP_TIMEOUT, log, read, write, normpath, HellocConnection, ExitHandler

class FileMessage:
    def __init__(self, msg):
        assert FileMessage.isfile(msg)
        self.filename = msg.chat.filename
        self.data = msg.chat.data

    def save(self):
        write(self.filename, self.data)

    @staticmethod
    def isfile(msg):
        return msg.type == Message.CHAT and msg.chat.type == Message.Chat.FILE

PORT = 8080
class HellocClient:
    def __init__ (self, server, port):
        self.server = server
        self.port = port
        self.soc = None
        self.socthread = None
        self.id = 0

    def newId(self):
        self.id +=1
        return self.id

    def login(self, username, password):
        msg = Message()
        msg.id = self.newId()
        msg.type = Message.LOGIN_REQ
        login = msg.login
        login.username = username
        login.password = password
        self.socthread.send_message(msg)

    def get_friends(self):
        msg = Message()
        msg.id = self.newId()
        msg.type = Message.GET_FRIENDS
        self.socthread.send_message(msg)

    def send_chat(self, userid, type=Message.Chat.TEXT, data="", filename=""):
        msg = Message()
        msg.id = self.newId()
        msg.type = Message.CHAT
        msg.chat.peer_id = userid
        msg.chat.type = type
        if data:
            msg.chat.data = data
        if filename:
            msg.chat.filename = filename
        self.socthread.send_message(msg)

    def send_file(self, userid, filepath):
        filename = os.path.basename(filepath)
        data = read(filepath)
        self.send_chat(userid, type=Message.Chat.FILE, \
            data=data, filename=filename)

    def handle_message(self, msg):
        #log("Client handle message", msg)
        if FileMessage.isfile(msg):
            FileMessage(msg).save()
        pass

    def start (self, username, password):
        self.soc = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.soc.connect((self.server, self.port))
        log("client started")
        self.socthread = HellocConnection(self.soc, self.handle_message)
        self.login(username, password)
        self.get_friends()

    def stop(self):
        log("Client stop")
        self.socthread.stop()


if __name__ == "__main__":
    args = sys.argv
    username = len(args) > 1 and args[1] or "user1"
    password = len(args) > 2 and args[2] or "helloc"
    ExitHandler.setup()
    client = HellocClient(LOCAL_HOST, PORT)
    ExitHandler.registerExitHandler(client.stop)
    client.start(username, password)
    if 1 : #len(args) > 1:
        # threading.Timer(5.0, client.stop).start()
        client.send_chat(2, data="Hello, I am python client")
        #client.send_file(1, normpath("../../test-data/WelcomeScan.jpg"))
    asyncore.loop(timeout = LOOP_TIMEOUT)
