// This is the header for the tpl functions that are used in ffmpeg for storing the intermediate AV data (that is, the raw streams, in chunks for the MR jobs).
// Chunks (for now) are simply an abstraction of an AVPacket.

#include "tpl.h"
#include <libavformat/avformat.h>
#include <stdint.h> // (For the C99 types).


int read_avstream_chunk_from_memory(void *opaque, int(*)(void *opaque, uint8_t *buf, int buf_size), AVFormatContext *os, AVStream **stream);
int read_avstream_chunk_from_fd(int fd, AVFormatContext *os, AVStream **stream);
int read_avstream_chunk_from_file(FILE *file, AVFormatContext *os, AVStream **stream);

int write_avstream_chunk_to_memory(AVStream *stream, uint8_t **unallocd_buffer, int *size);
int write_avstream_chunk_to_fd(AVStream *stream, int fd);
int write_avstream_chunk_to_file(AVStream *stream, FILE *file);


int read_avpacket_chunk_from_memory(void *opaque, int(*)(void *opaque, uint8_t *buf, int buf_size), AVPacket *pkt);
int read_avpacket_chunk_from_fd(int fd, AVPacket *pkt);
int read_avpacket_chunk_from_file(FILE *file, AVPacket *pkt);

int write_avpacket_chunk_to_memory(AVPacket *pkt, uint8_t **unallocd_buffer, int *size);
int write_avpacket_chunk_to_fd(AVPacket *pkt, int fd);
int write_avpacket_chunk_to_file(AVPacket *pkt, FILE *file);