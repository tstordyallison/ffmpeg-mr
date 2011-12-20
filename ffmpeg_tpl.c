#include "tpl.h"
#include "ffmpeg_tpl.h"
#include <libavformat/avformat.h>
#include <stdio.h>
#include <unistd.h>

// -----------------------------------------------------------------
// This format is a bit of a hack.
// The way that the AVPackets are stored needs changed so that we use the unbounded array support in 
// tpl, rather than just loads of things one after the other.
// ----------------------------------------------------------------
// This is the format definition for the AVStream that we store.
// Most of the data we store for a stream, is really the codec context.
// We don't store any of the transient information about the stream - this is not what we are bothered about.
// This takes most of its def. from the copy_stream method found in ffmpeg.c

#define AVSTREAM_TPL_FORMAT "iiiiiiiiiiiiiiIiiiiiiiiiiiiB"
/*
----- STREAM ------
 i  =   int disposition; < AV_DISPOSITION_* bit field
 ii =   AVRational time_base;
 ii =   AVRational sample_aspect_ratio
----- CODEC  ------
 i  =   int bits_per_raw_sample;
 i  =   enum AVChromaLocation chroma_sample_location;
 i  =   enum CodecID codec_id;
 i  =   enum AVMediaType codec_type;
 i  =   int bit_rate;
 i  =   int rc_max_rate;
 i  =   int rc_buffer_size;
 ii =   AVRational time_base;
 I  =   int64_t channel_layout;
 i  =   int sample_rate;
 i  =   int channels; 
 i  =   enum AVSampleFormat sample_fmt;
 i  =   int frame_size;
 i  =   enum AVAudioServiceType audio_service_type;
 i  =   int block_align;
 i  =   enum PixelFormat pix_fmt;
 i  =   int width;
 i  =   int height;
 i  =   int has_b_frames;
 ii =   AVRational sample_aspect_ratio;
 B  =   uint8_t *extradata/int extradata_size; (as a byte stream)
 */

int read_avstream_chunk_from_memory(void *opaque, int(*read_data)(void *opaque, uint8_t *buf, int buf_size), AVFormatContext *os, AVStream **stream){
}

int read_avstream_chunk_from_fd(int fd, AVFormatContext *os, AVStream **new_stream){
    
    AVStream *stream;
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    *new_stream = av_new_stream(os, 0);
    if (!*new_stream) {
        fprintf(stderr, "Could not alloc stream\n");
        return -1;
    }
    
    stream = *new_stream;
    
    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &(stream->disposition),
                 &(stream->time_base.num),
                 &(stream->time_base.den),
                 &(stream->sample_aspect_ratio.num),
                 &(stream->sample_aspect_ratio.den),
                 &(stream->codec->bits_per_raw_sample),
                 &(stream->codec->chroma_sample_location),
                 &(stream->codec->codec_id),
                 &(stream->codec->codec_type),
                 &(stream->codec->bit_rate),
                 &(stream->codec->rc_max_rate),
                 &(stream->codec->rc_buffer_size),
                 &(stream->codec->time_base.num),
                 &(stream->codec->time_base.den),
                 &(stream->codec->channel_layout),
                 &(stream->codec->sample_rate),
                 &(stream->codec->channels),
                 &(stream->codec->sample_fmt),
                 &(stream->codec->frame_size),
                 &(stream->codec->audio_service_type),
                 &(stream->codec->block_align),
                 &(stream->codec->pix_fmt),
                 &(stream->codec->width),
                 &(stream->codec->height),
                 &(stream->codec->has_b_frames),
                 &(stream->codec->sample_aspect_ratio.num),
                 &(stream->codec->sample_aspect_ratio.den),
                 &data);
    
    ret = tpl_load(tn, TPL_FD, fd);
    if(ret < 0)
        return ret;
    
    tpl_unpack(tn,0);
    
    stream->codec->extradata_size = data.sz;
    stream->codec->extradata = data.addr;

    stream->codec->codec_tag = 0;
    
    {
        // Nicely doctored code from the insides of ffmpeg.c
         
        AVCodecContext *codec = stream->codec;

        stream->stream_copy = -1;
        
        // WTF - this needs sorted out.
        //codec->time_base = stream->time_base;
        //av_reduce(&codec->time_base.num, &codec->time_base.den, codec->time_base.num, codec->time_base.den, INT_MAX);
        
        switch(codec->codec_type) {
            case AVMEDIA_TYPE_AUDIO:
                if(codec->block_align == 1 && codec->codec_id == CODEC_ID_MP3)
                    codec->block_align= 0;
                if(codec->codec_id == CODEC_ID_AC3)
                    codec->block_align= 0;
                break;
            case AVMEDIA_TYPE_VIDEO:
                //if (!codec->sample_aspect_ratio.num) {
                    codec->sample_aspect_ratio =
                    stream->sample_aspect_ratio =
                        stream->sample_aspect_ratio.num ? stream->sample_aspect_ratio :
                        stream->codec->sample_aspect_ratio.num ?
                        stream->codec->sample_aspect_ratio : (AVRational){0, 1};
                //}
                break;
            case AVMEDIA_TYPE_SUBTITLE:
            case AVMEDIA_TYPE_DATA:
                break;
            default:
                return -1;
        }
    }
    
    tpl_free(tn);
    
    return ret;
}

int read_avstream_chunk_from_file(FILE *file, AVFormatContext *os, AVStream **stream){
}

int write_avstream_chunk_to_memory(AVStream *stream, uint8_t **unallocd_buffer, int *size){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    data.sz = stream->codec->extradata_size; 
    data.addr = stream->codec->extradata;
    
    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &(stream->disposition),
                 &(stream->time_base.num),
                 &(stream->time_base.den),
                 &(stream->sample_aspect_ratio.num),
                 &(stream->sample_aspect_ratio.den),
                 &(stream->codec->bits_per_raw_sample),
                 &(stream->codec->chroma_sample_location),
                 &(stream->codec->codec_id),
                 &(stream->codec->codec_type),
                 &(stream->codec->bit_rate),
                 &(stream->codec->rc_max_rate),
                 &(stream->codec->rc_buffer_size),
                 &(stream->codec->time_base.num),
                 &(stream->codec->time_base.den),
                 &(stream->codec->channel_layout),
                 &(stream->codec->sample_rate),
                 &(stream->codec->channels),
                 &(stream->codec->sample_fmt),
                 &(stream->codec->frame_size),
                 &(stream->codec->audio_service_type),
                 &(stream->codec->block_align),
                 &(stream->codec->pix_fmt),
                 &(stream->codec->width),
                 &(stream->codec->height),
                 &(stream->codec->has_b_frames),
                 &(stream->codec->sample_aspect_ratio.num),
                 &(stream->codec->sample_aspect_ratio.den),
                 &data);
    
    tpl_pack(tn,0);
    ret = tpl_dump(tn, TPL_MEM, unallocd_buffer, size);
    tpl_free(tn);
    
    return ret;
}

int write_avstream_chunk_to_fd(AVStream *stream, int fd){
    uint8_t *buffer;
    int size, ret;
    ret = write_avstream_chunk_to_memory(stream, &buffer, &size);
    if(ret == 0)
        write(fd, buffer, size);
    free(buffer);
    return ret;
}

int write_avstream_chunk_to_file(AVStream *stream, FILE *file){
    
    
}

// This is the format definition for the AVPackets that we store.
#define AVPACKET_TPL_FORMAT "IIiiIiB"
/*
 I = int64_t pts;
 I = int64_t dts;
 i = int   flags;
 i = int   duration;
 I = int64_t convergence_duration;
 i = int   stream_index;
 //A(S(iiB)) = side data struct (type, size, data).
 //i = int side_data_elems;
 B = uint8_t *data/size;
 */

int read_avpacket_chunk_from_memory(void *opaque, int(*read_data)(void *opaque, uint8_t *buf, int buf_size), AVPacket *pkt){
}

int read_avpacket_chunk_from_fd(int fd, AVPacket *pkt){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    tn = tpl_map(AVPACKET_TPL_FORMAT,
                 &(pkt->pts), 
                 &(pkt->dts),
                 &(pkt->flags),
                 &(pkt->duration),
                 &(pkt->convergence_duration),
                 &(pkt->stream_index),
                 &data);
    
    ret = tpl_load(tn, TPL_FD, fd);
    if(ret < 0)
        return ret;
    
    tpl_unpack(tn,0);
    
    pkt->size = data.sz;
    pkt->data = data.addr;
    
    pkt->destruct = av_destruct_packet;
    
    tpl_free(tn);
    
    return ret;
}

int read_avpacket_chunk_from_file(FILE *file, AVPacket *pkt){
}

int write_avpacket_chunk_to_memory(AVPacket *pkt, uint8_t **unallocd_buffer, int *size){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    data.sz = pkt->size;
    data.addr = pkt->data;
    
    tn = tpl_map(AVPACKET_TPL_FORMAT,
                 &(pkt->pts), 
                 &(pkt->dts),
                 &(pkt->flags),
                 &(pkt->duration),
                 &(pkt->convergence_duration),
                 &(pkt->stream_index),
                 &data);
    
    tpl_pack(tn,0);
    ret = tpl_dump(tn, TPL_MEM, unallocd_buffer, size);
    tpl_free(tn);
    
    return ret;
    
}

int write_avpacket_chunk_to_fd(AVPacket *pkt, int fd){
    uint8_t *buffer;
    int size, ret;
    ret = write_avpacket_chunk_to_memory(pkt, &buffer, &size);
    if(ret == 0)
        write(fd, buffer, size);
    free(buffer);
    return ret;
}

int write_avpacket_chunk_to_file(AVPacket *pkt, FILE *file){}
