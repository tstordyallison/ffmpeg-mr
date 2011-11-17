// This is the header for the tpl functions that are used in ffmpeg for storing the intermediate AV data (that is, the raw streams, in chunks for the MR jobs).
// Chunks (for now) are simply an abstraction of an AVPacket.

#include "tpl.h"
#include <libavformat/avformat.h>

int read_avstream_chunk_from_file(int fd, AVStream *stream);
int write_avstream_chunk_to_file(AVStream *stream, int fd);

int read_avpacket_chunk_from_file(int fd, AVPacket *pkt);
int write_avpacket_chunk_to_file(AVPacket *pkt, int fd);

