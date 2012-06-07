SRC_DIR = .
JAVA_DIR = ../client/java/generic/src
JAVA_PACKAGE_DIR = $(JAVA_DIR)/helloc/protocol
PY_DIR = ../server/python
TOOLS_DIR = ../tools

all: $(JAVA_PACKAGE_DIR)/Message.java	$(PY_DIR)/messages_pb2.py

$(JAVA_PACKAGE_DIR)/Message.java: $(SRC_DIR)/messages.proto
	$(TOOLS_DIR)/protoc -I=$(SRC_DIR) --java_out=$(JAVA_DIR) $<

$(PY_DIR)/messages_pb2.py: $(SRC_DIR)/messages.proto
	$(TOOLS_DIR)/protoc -I=$(SRC_DIR) --python_out=$(PY_DIR) $<