#!/bin/sh

#export JAVA_HOME=/usr/lib/jvm/java-6-openjdk
export JAVA_HOME=/usr/lib/jvm/java-6-sun

# -----------------------------------
# Download and build script for EMR
# -----------------------------------

# Get yasm and install it
wget http://www.tortall.net/projects/yasm/releases/yasm-1.1.0.tar.gz -O- | tar zxvf -
cd yasm-1.1.0
./configure --disable-nls --prefix=/usr
make
sudo make install
cd ..
rm -rf ./yasm-1.1.0

# Install CVS.
sudo apt-get install cvs

# Download sources.
git clone --depth 1 git://source.ffmpeg.org/ffmpeg.git ffmpeg
git clone --depth 1 git://git.videolan.org/x264.git x264
cvs -z3 -d:pserver:anonymous@lame.cvs.sourceforge.net:/cvsroot/lame co -P lame

wget http://dl.dropbox.com/u/8444884/ffmpeg-mr.zip -O /tmp/ffmpeg-mr.zip
unzip -q /tmp/ffmpeg-mr.zip -d ./ffmpeg-mr && rm /tmp/ffmpeg-mr.zip

wget http://mirror.catn.com/pub/apache/hadoop/common/hadoop-0.20.203.0/hadoop-0.20.203.0rc1.tar.gz  -O hadoop.tar.gz
tar zxvf hadoop.tar.gz 
rm hadoop.tar.gz

wget http://downloads.sourceforge.net/faac/faac-1.28.tar.gz -O faac.tar.gz
tar zxvf faac.tar.gz  
rm faac.tar.gz

# Make the output directory.
if [ ! -d "$DIRECTORY" ]; then
    mkdir $HOME/build-ouptut/
fi

# Config & build FAAC
cd faac-1.28
./configure --enable-shared=yes --with-pic --prefix="$HOME/build-ouptut"
make
make install
cd ..

# Config & build lame
cd lame
./configure --enable-shared --prefix="$HOME/build-ouptut" && \
make && \
make install
cd ..

# Config & build x264
cd x264
./configure --enable-shared --enable-pic --disable-lavf --disable-cli --prefix="$HOME/build-ouptut" && \
make && make install
cd ..

# Config & build ffmpeg
cd ffmpeg
./configure \
	--enable-gpl \
	--enable-nonfree \
	--enable-libfaac \
	--enable-libx264 \
	--enable-libmp3lame \
	--enable-shared \
	--enable-pic \
	--extra-ldflags="-L$HOME/build-ouptut/lib" \
	--extra-cflags="-I$HOME/build-ouptut/include" \
	--prefix="$HOME/build-ouptut"
make && make install-libs install-headers
cd ..

# Build ffmpeg-mr
cd ffmpeg-mr
make
cd ..

cd /Users/tom/Code/fyp/ffmpeg-mr/ffmpeg-mr/ && ant hadoop_build && ant testing_build

~/Code/elastic-mapreduce-ruby/elastic-mapreduce --create --name "Build: 64bit FFmpeg-MR" --instance-group master --bid-price 0.15 --instance-type m1.large --instance-count 1 --bootstrap-action s3://ffmpeg-mr/build/build.sh 
~/Code/elastic-mapreduce-ruby/elastic-mapreduce --create --name "Build: 32bit FFmpeg-MR" --instance-group master --bid-price 0.10 --instance-type m1.small --instance-count 1 --bootstrap-action s3://ffmpeg-mr/build/build.sh 

# Build command.
hadoop fs -get s3://ffmpeg-mr/build/build.sh build.sh && chmod 755 build.sh && ./build.sh

./run.sh testing.ChunkTest s3n://ffmpeg-mr/movies/Test.mkv ./Test.mkv.seq

# Create build tar for upload.
tar cf build.tar build*
hadoop fs -put ./build.tar s3n://ffmpeg-mr/build.tar
hadoop fs -put ./build.tar s3n://ffmpeg-mr/build64.tar

# Nice top.
sudo apt-get install htop && htop
sudo apt-get install bmon && bmon