#!/bin/python

import socket,sys,re,os,threading,traceback
from optparse import OptionParser

from Utils import backtrace, log, LOCAL_HOST

PORT = 8080

class Server:
	def __init__ (self, host = LOCAL_HOST, port = PORT, onClientAccepted = None):
		self.host = host
		self.port = port
		self.soc = None
		self.running = True
		self.onClientAccepted = onClientAccepted;
		self.clients = []

	def addClient (self, client):
		if not client in self.clients:
			self.clients.append(client)

	def removeClient (self, client):
		self.clients.remove(client)

	def broadcast (self, msg, condition):
		for client in self.clients:
			if condition(client):
				client.send(msg)

	def stop(self):
		log("server stop")
		self.running = False
		self.soc.close()

	def run (self):
		self.clients = []
		self.soc = soc = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		soc.bind((self.host, self.port))
		soc.listen(10)
		log("server started, lisening")
		while self.running:
			log("accepting")
			try:
				(client, client_addr) = soc.accept()
				log("accepted:", client, client_addr)
				self.addClient(client)
				if self.onClientAccepted:
					self.onClientAccepted(client, client_addr);
			except Exception, e:
				log(e)
				pass

		log("server closed")
		soc.close()


if __name__ == "__main__":
	Server().run();