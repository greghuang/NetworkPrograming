include Makefile.inc
NAME        = network-programming
VERSION     = 1.0
PKGNAME     = ${NAME}-${VERSION}
SRCPATH     = src
OUTPATH     = class
LIBPATH     = lib

PROTOCOL_PATH = pbdef
PROTOCOL      = $(PROTOCOL_PATH)/Protocol.proto

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

begin: all 
	@ant


SERVER_LIB=$(shell pwd)/${LIBPATH}/${PROTOBUF_JAVA_RUNTIME} 


all:${SERVER_LIB}  PROTOCOL 

	@echo "finisth build librarys"

PHONY=$(PROTOCOL)

PROTOCOL:
	$(PROTOC)  --proto_path=$(PROTOCOL_PATH) --java_out=$(SRCPATH) $(PROTOCOL)

${SERVER_LIB}: ${PROTOBUF_JAVA_RUNTIME}
	mkdir -p ./${LIBPATH}
	cp ${PROTOBUF_JAVA_RUNTIME} ./${LIBPATH};

${PROTOBUF_JAVA_RUNTIME}: ${PROTOC} 
	alias protoc=$(PROTOC); \
	export LD_LIBRARY_PATH=${PROTOBUF_LIB_PATH}:$${LD_LIBRARY_PATH} ; \
	cd ${PROTOBUF_JAVA_RUNTIME_SRC}; \
	$(PROTOC) --java_out=src/main/java -I../src \
	../src/google/protobuf/descriptor.proto; \
	mkdir -p build; \
	javac -d build src/main/java/com/google/protobuf/*; \
	jar   -cvf $@ -C build/ .;
	mv ${PROTOBUF_JAVA_RUNTIME_SRC}/$@ .;

$(PROTOC):  
	mkdir -p $(PROTOBUF_DIR);\
	tar xjf $(THIRD_PARTY_DIR)/$(PROTOBUF_PKG).$(ARCHIVE_TYPE) -C $(PROTOBUF_DIR);\
	cd $(PROTOBUF_DIR); \
	cd $(PROTOBUF_PKG); \
	./configure --prefix=$(PROTOBUF_DIR)/build $(CXXFLAGS); \
	make; \
	make install; \
	cd -;



clean:
	rm -r $(LIBPATH)
	rm -r $(OUTPATH)
