#!/bin/bash

# Get yasm and install it
# wget http://www.tortall.net/projects/yasm/releases/yasm-1.1.0.tar.gz -O- | tar zxvf -
# cd yasm-1.1.0
# ./configure --disable-nls --prefix=/usr
# make
# sudo make install
# cd ..
# rm -rf ./yasm-1.1.0

# Install CVS.
# sudo apt-get install cvs
PLATFORM=`getconf LONG_BIT`

# Perform the correct build, and upload it.
if [ $PLATFORM == "64" ]
then
    hadoop fs -get s3://ffmpeg-mr/build/build64.tar ./build.tar && tar xf build.tar && cd build \
	&& rm -rf ./ffmpeg-mr && wget http://dl.dropbox.com/u/8444884/ffmpeg-mr.zip -O /tmp/ffmpeg-mr.zip && \
	unzip -o -q /tmp/ffmpeg-mr.zip -d ./ffmpeg-mr && rm /tmp/ffmpeg-mr.zip && cd ffmpeg-mr && chmod 755 run.sh && \
	make
	if [ -e libffmpeg-mr.so ]
	then
		hadoop fs -rm s3://ffmpeg-mr/lib64/libffmpeg-mr.so 
		hadoop fs -put libffmpeg-mr.so s3://ffmpeg-mr/lib64/libffmpeg-mr.so
	else
		exit -1
	fi
else
    hadoop fs -get s3://ffmpeg-mr/build/build.tar ./build.tar && tar xf build.tar && cd build \
	&& rm -rf ./ffmpeg-mr && wget http://dl.dropbox.com/u/8444884/ffmpeg-mr.zip -O /tmp/ffmpeg-mr.zip && \
	unzip -o -q /tmp/ffmpeg-mr.zip -d ./ffmpeg-mr && rm /tmp/ffmpeg-mr.zip && cd ffmpeg-mr && chmod 755 run.sh && \
	make
	if [ -e libffmpeg-mr.so ]
	then
		hadoop fs -rm s3://ffmpeg-mr/lib/libffmpeg-mr.so 
		hadoop fs -put libffmpeg-mr.so s3://ffmpeg-mr/lib/libffmpeg-mr.so
	else
		exit -1
	fi
fi
