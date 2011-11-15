// This is the header for the tpl functions that are used in ffmpeg for storing the intermediate AV data (that is, the raw streams, in chunks for the MR jobs).
// Chunks (for now) are simply an abstraction of an AVPacket.

#include <libavformat/avformat.h>

int read_avpacket_chunk_from_file(AVPacket *pkt, FILE fd);
int write_avpacket_chunk_from_file(AVPacket *pkt, FILE fd);
