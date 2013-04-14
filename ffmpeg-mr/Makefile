CC=gcc
CCPP=g++
OUTPUT=libffmpeg-mr.so

JNIHEADERS=$(JAVA_HOME)/include
JNIHEADERS2=$(JAVA_HOME)/include/linux
FFMPEGOUTPUT=../ffmpeg

CFLAGS= -O3 -fPIC -DDEBUG=1 -DAWSBUILD=1 -D__STDC_CONSTANT_MACROS -c -Wall -I. -I$(FFMPEGOUTPUT) -I$(JNIHEADERS) -I$(JNIHEADERS2)
LDFLAGS=-L../../build-ouptut/lib

all: $(OUTPUT)

$(OUTPUT): JNI.o SharedUtil.o ffmpeg_tpl.o Muxer.o Demuxer.o Transcoder.o Merger.o
	$(CCPP) -shared -Wl ffmpeg_tpl.o tpl.o JNI.o SharedUtil.o Demuxer.o Muxer.o Transcoder.o Merger.o -lavcodec -lavformat -lavutil -lmp3lame -lswscale -lx264 -lfaac -lz -lbz2 $(LDFLAGS) -o $(OUTPUT)

JNI.o: JNI.cpp
	$(CCPP) $(CFLAGS) JNI.cpp

SharedUtil.o: SharedUtil.cpp
	$(CCPP) $(CFLAGS) SharedUtil.cpp

ffmpeg_tpl.o: ffmpeg_tpl.c tpl.o
	$(CC) $(CFLAGS) ffmpeg_tpl.c

tpl.o: tpl.c
	$(CC) $(CFLAGS) tpl.c

Demuxer.o: Demuxer.cpp
	$(CCPP) $(CFLAGS) Demuxer.cpp
	
Muxer.o: Muxer.cpp
	$(CCPP) $(CFLAGS) Muxer.cpp
	
Merger.o: Merger.cpp
	$(CCPP) $(CFLAGS) Merger.cpp

Transcoder.o: Transcoder.cpp
	$(CCPP) $(CFLAGS) Transcoder.cpp

clean:
	rm -rf *.o