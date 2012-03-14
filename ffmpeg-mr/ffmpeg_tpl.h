// This is the header for the tpl functions that are used in ffmpeg for storing the intermediate AV data (that is, the raw streams, in chunks for the MR jobs).
// Chunks (for now) are simply an abstraction of an AVPacket.

#include "tpl.h"
#include <libavformat/avformat.h>
#include <stdint.h> // (For the C99 types).

//------------------ tpl_gather API ------------------//
typedef struct TPLImageRef {
    uint8_t *data;
    size_t size;
} TPLImageRef;

int tpl_gather_image_list(uint8_t *data, size_t size, TPLImageRef **list, size_t *list_size);


//------------------ AVStream Header Read/Write ------------------//

int read_avstream_chunk_as_cc_from_memory(uint8_t *buf, size_t buf_size, AVCodecContext **cc, AVRational *stream_time_base, AVRational *stream_frame_rate);
int read_avstream_chunk_from_memory(uint8_t *buf, int buf_size, AVFormatContext *os, AVStream **stream);
int read_avstream_chunk_from_fd(int fd, AVFormatContext *os, AVStream **stream);

int write_avstream_chunk_as_cc_to_memory(AVCodecContext *codec_ref, AVRational stream_time_base, AVRational stream_frame_rate, uint8_t **unallocd_buffer, int *size);
int write_avstream_chunk_to_memory(AVStream *stream, uint8_t **unallocd_buffer, int *size);
int write_avstream_chunk_to_fd(AVStream *stream, int fd);

//------------------ AVPacket Read/Write ------------------//

int read_avpacket_chunk_from_memory(uint8_t *buf, size_t buf_size, AVPacket *pkt);
int read_avpacket_chunk_from_fd(int fd, AVPacket *pkt);

int write_avpacket_chunk_to_memory(AVPacket *pkt, uint8_t **unallocd_buffer, int *size);
int write_avpacket_chunk_to_fd(AVPacket *pkt, int fd);