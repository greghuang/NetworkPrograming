
NAME        = network-programming
VERSION     = 1.0
PKGNAME     = ${NAME}-${VERSION}
SRCPATH     = src
OUTPATH     = class
LIBPATH     = lib

ifndef BUILD_TAG
BUILD_TAG=`date +%Y%m%d`
endif

begin:
	@ant

clean:
	rm -r $(LIBPATH)
	rm -r $(OUTPATH)
