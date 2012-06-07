#!/bin/python

from Server import Server
from Utils import Message
import asyncore, struct, threading, socket

from Utils import LOCAL_HOST, LOOP_TIMEOUT, normpath, log, HellocConnection, ExitHandler

PORT = 8080

class User:
    def __init__(self, userid, username, password):
        self.userid = userid
        self.username = username
        self.password = password
        self.online = False

    def checkUser(self, username, password):
        #FIXME Encryption
        return self.username == username and self.password == password

class DataCenter:
    instance = None

    def __init__(self):
        assert DataCenter.instance is None
        self.users = {1: User(1, "user1", "helloc"),
            2: User(2, "user2", "helloc"),
            3: User(3, "user3", "helloc")}
        self.statusListeners = []
        self.chatListener = []
        self.chats = []

    @staticmethod
    def getInstance():
        if DataCenter.instance is None:
            DataCenter.instance = DataCenter()
        return DataCenter.instance

    def addChatListener(self, listener):
        self.chatListener.append(listener)

    def removeChatListener(self, listener):
        self.chatListener.remove(listener)

    def addStatusListener(self, listener):
        self.statusListeners.append(listener)

    def removeStatusListener(self, listener):
        self.statusListeners.remove(listener)

    def findUserByName(self, username):
        for u in self.users.values():
            if u.username == username:
                return u
        return None

    def setUserStatus(self, userid, status):
        u = self.users[userid]
        if u.online != status:
            old_status = u.online
            u.online = status
            for l in self.statusListeners:
                l(u.userid, old_status, status)

    def addChatMessage(self, msg):
        if msg.chat.userid == msg.chat.peer_id:
            # Don't allow to send message to yourself
            return;
        self.chats.append(msg)
        for l in self.chatListener:
            if l(msg):
               msg.chat.readed = True
               break

    def login(self, username, password):
        u = self.findUserByName(username)
        if u and not u.online and u.checkUser(username, password):
            self.setUserStatus(u.userid, True)
            return u
        else:
            return None

    def logout(self, userid):
        self.setUserStatus(userid, False)

    def get_friends(self, userid):
        return self.users

class ClientPort:
    def __init__(self, socthread=None, chat_message_handler=None, logout_listener=None):
        self.socthread = socthread
        self.msg_handlers = { Message.LOGIN_REQ : self.handle_login,
            Message.GET_FRIENDS: self.handle_get_friends,
            Message.CHAT: self.handle_chat_message,
        }
        self.id = 0
        self.user = None
        self.dc = DataCenter.getInstance()
        self.chat_message_handler = chat_message_handler
        self.logout_listener = logout_listener

    def get_userid(self):
        assert self.user
        if self.user:
            return self.user.userid
        else:
            return -1

    def stop(self):
        log("ClientPort stop")
        self.socthread.stop()

    def set_socthread(self, socthread):
        self.socthread = socthread

    def newId(self):
        self.id +=1
        return self.id

    def newMessage(self):
        msg = Message()
        msg.id = self.newId()
        return msg

    def userStatusChangedListener(self, userid, old_status, new_status):
        if self.user.userid == userid:
            return
        assert old_status != new_status
        self.update_friends()

    def chatListener(self, msg):
        assert self.has_login()
        if self.user.userid == msg.chat.peer_id:
            self.send_message(msg)

    def send_message(self, msg):
        self.socthread.send_message(msg)

    def response(self, tomsg, status):
        msg = Message()
        msg.id = self.newId()
        msg.type = Message.RESPONSE
        msg.response.rspId = tomsg.id
        msg.response.status = status
        self.socthread.send_message(msg)

    def responseLogin(self):
        msg = Message()
        msg.id = self.newId()
        msg.type = Message.LOGIN_RESPONSE
        if self.user:
            msg.login_response.status = Message.OK
            msg.login_response.userid = self.user.userid
        else:
            msg.login_response.status = Message.FAILED
        self.socthread.send_message(msg)

    def has_login(self):
        return self.user is not None

    def handle_login(self, msg):
        u = self.dc.login(msg.login.username, msg.login.password)
        if u:
            self.user = u
            self.dc.addStatusListener(self.userStatusChangedListener)
            self.dc.addChatListener(self.chatListener)
        self.responseLogin()

    def logout(self):
        self.dc.removeChatListener(self.chatListener)
        self.dc.removeStatusListener(self.userStatusChangedListener)
        self.dc.logout(self.user.userid)
        self.user = None
        if self.logout_listener:
            self.logout_listener(self)

    def update_friends(self):
        if self.has_login():
            msg = self.newMessage()
            msg.type = Message.FRIENDS_LIST
            for u in self.dc.get_friends(self.user.userid).values():
                f = msg.friends_list.friends.add()
                f.userid = u.userid
                f.username = u.username
                if u.online:
                    f.online_status = Message.ONLINE
                else:
                    f.online_status = Message.OFFLINE
            self.socthread.send_message(msg)

    def handle_get_friends(self, msg):
        self.update_friends()

    def handle_chat_message(self, msg):
        if self.has_login():
            msg.chat.userid = self.user.userid
            self.dc.addChatMessage(msg)

    def handle_message(self, msg):
        #log("Server handle_message", msg)
        assert msg.type in self.msg_handlers
        self.msg_handlers[msg.type](msg)

    def handle_soc_closed(self):
        if self.has_login():
           self.logout()

class HellocServer(asyncore.dispatcher):
    def __init__ (self, host = LOCAL_HOST, port = PORT):
        asyncore.dispatcher.__init__(self)
        self.client_ports = []
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.set_reuse_addr()
        self.bind((host, port))
        self.listen(5)

    # def writable(self):
    #     return False

    # def readable(self):
    #     return False

    def handle_accept(self):
        soc, addr = self.accept()
        self.onClientAccepeted(soc, addr)

    def stop(self):
        map(ClientPort.stop, self.client_ports)

    def client_logout(self, client_port):
        self.client_ports.remove(client_port)

    def onClientAccepeted(self, client_soc, client_addr):
        log("Server clientAccepted", client_soc)
        client_port = ClientPort(client_soc, \
            logout_listener = self.client_logout)
        self.client_ports.append(client_port)
        socthread = HellocConnection(client_soc, client_port.handle_message, \
            soc_closed=client_port.handle_soc_closed)
        client_port.set_socthread(socthread)

    def run(self):
        asyncore.loop(timeout=LOOP_TIMEOUT)


if __name__ == "__main__":
    server = HellocServer()
    ExitHandler.setup();
    ExitHandler.registerExitHandler(server.stop)
    server.run()
