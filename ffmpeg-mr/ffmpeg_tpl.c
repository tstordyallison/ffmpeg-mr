#include "tpl.h"
#include "ffmpeg_tpl.h"
#include <libavformat/avformat.h>
#include <stdio.h>
#include <unistd.h>

#define DEBUG 1
#define DEBUG_PRINT 0

/* -----------------------------------------------------------------
 This format is a bit of a hack.
 The way that the AVPackets are stored needs changed so that we use the unbounded array support in 
 tpl, rather than just loads of things one after the other.
 ----------------------------------------------------------------
 This is the format definition for the AVStream that we store.
 Most of the data we store for a stream, is really the codec context.
 We don't store any of the transient information about the stream - this is not what we are bothered about.
 This takes most of its def. from the copy_stream method found in ffmpeg.c
*/
#define AVSTREAM_TPL_FORMAT "iiiiiiiiiiiiiIiiiiiiiiiiiiiB"
/*
----- STREAM ------
 ii =   AVRational time_base
 ii =   AVRational frame_rate
 ii =   AVRational sample_aspect_ratio
----- CODEC  ------
 i  =   int bits_per_raw_sample;
 i  =   enum AVChromaLocation chroma_sample_location;
 i  =   enum CodecID codec_id;
 i  =   enum AVMediaType codec_type;
 i  =   int bit_rate;
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
 i  =   ticks_per_frame
 B  =   uint8_t *extradata/int extradata_size; (as a byte stream)
 */

int read_avstream_chunk_as_cc_from_memory(uint8_t *buf, size_t buf_size, AVCodecContext **codec_ref, AVRational *stream_time_base, AVRational *stream_frame_rate){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    AVCodecContext *codec;
    int dummy;
    
    if(*codec_ref == NULL){
        *codec_ref = avcodec_alloc_context3(NULL);
    }
    
    codec = *codec_ref;

    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &(stream_time_base->num),
                 &(stream_time_base->den),
                 &(stream_frame_rate->num),
                 &(stream_frame_rate->den),
                 &(dummy),
                 &(dummy),
                 &(codec->bits_per_raw_sample),
                 &(codec->chroma_sample_location),
                 &(codec->codec_id),
                 &(codec->codec_type),
                 &(codec->bit_rate),
                 &(codec->time_base.num),
                 &(codec->time_base.den),
                 &(codec->channel_layout),
                 &(codec->sample_rate),
                 &(codec->channels),
                 &(codec->sample_fmt),
                 &(codec->frame_size),
                 &(codec->audio_service_type),
                 &(codec->block_align),
                 &(codec->pix_fmt),
                 &(codec->width),
                 &(codec->height),
                 &(codec->has_b_frames),
                 &(codec->sample_aspect_ratio.num),
                 &(codec->sample_aspect_ratio.den),
                 &(codec->ticks_per_frame),
                 &data);
    
    ret = tpl_load(tn, TPL_MEM|TPL_EXCESS_OK, buf, buf_size);
    if(ret < 0)
        return ret;
    
    tpl_unpack(tn, 0);
    tpl_free(tn);

    if(DEBUG_PRINT) {
        fprintf(stderr, "Loaded chunk with CTB: %d/%d (1/=%2.2f)\n", codec->time_base.num, codec->time_base.den, (float)codec->time_base.den/codec->time_base.num);
        fprintf(stderr, "Loaded chunk with STB: %d/%d (1/=%2.2f)\n", stream_time_base->num, stream_time_base->den, (float)stream_time_base->den/stream_time_base->num);
    }
    
    codec->extradata_size = data.sz;
    codec->extradata = data.addr;
    codec->codec_tag = 0;
    
    return 0;
}

int read_avstream_chunk_from_memory(uint8_t *buf, int buf_size, AVFormatContext *os, AVStream **stream){
    return -1;
}

int read_avstream_chunk_from_fd(int fd, AVFormatContext *os, AVStream **new_stream){
    
    AVStream *stream;
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    // TODO: replace with avformat_new_stream()
    *new_stream = av_new_stream(os, 0);
    if (!*new_stream) {
        fprintf(stderr, "Could not alloc stream\n");
        return -1;
    }
    
    stream = *new_stream;
    
    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &(stream->time_base.num),
                 &(stream->time_base.den),
                 &(stream->r_frame_rate.num),
                 &(stream->r_frame_rate.den),
                 &(stream->sample_aspect_ratio.num),
                 &(stream->sample_aspect_ratio.den),
                 &(stream->codec->bits_per_raw_sample),
                 &(stream->codec->chroma_sample_location),
                 &(stream->codec->codec_id),
                 &(stream->codec->codec_type),
                 &(stream->codec->bit_rate),
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
                 &(stream->codec->ticks_per_frame),
                 &data);
    
    ret = tpl_load(tn, TPL_FD, fd);
    if(ret < 0)
        return ret;
    
    tpl_unpack(tn,0);
    
    stream->codec->extradata_size = data.sz;
    stream->codec->extradata = data.addr;
    stream->codec->codec_tag = 0;
    
    if(DEBUG_PRINT) {
        fprintf(stderr, "Loaded stream CTB: %d/%d (1/=%2.2f)\n", stream->codec->time_base.num, stream->codec->time_base.den, (float)stream->codec->time_base.den/stream->codec->time_base.num);
        fprintf(stderr, "Loaded stream STB: %d/%d (1/=%2.2f)\n", stream->time_base.num, stream->time_base.den, (float)stream->time_base.den/stream->time_base.num);
        fprintf(stderr, "Loaded stream TPF: %d\n", stream->codec->ticks_per_frame);
    }
    
    tpl_free(tn);
    
    return ret;
}

int write_avstream_chunk_to_memory(AVStream *stream, uint8_t **unallocd_buffer, int *size){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    data.sz = stream->codec->extradata_size; 
    data.addr = stream->codec->extradata;
    
    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &(stream->time_base.num),
                 &(stream->time_base.den),
                 &(stream->r_frame_rate.num),
                 &(stream->r_frame_rate.den),
                 &(stream->sample_aspect_ratio.num),
                 &(stream->sample_aspect_ratio.den),
                 &(stream->codec->bits_per_raw_sample),
                 &(stream->codec->chroma_sample_location),
                 &(stream->codec->codec_id),
                 &(stream->codec->codec_type),
                 &(stream->codec->bit_rate),
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
                 &(stream->codec->ticks_per_frame),
                 &data);
    
    tpl_pack(tn,0);
    ret = tpl_dump(tn, TPL_MEM, unallocd_buffer, size);
    tpl_free(tn);
    
    if(DEBUG_PRINT) {
        fprintf(stderr, "Saved chunk with CTB: %d/%d (1/=%2.2f)\n", stream->codec->time_base.num, stream->codec->time_base.den, (float)stream->codec->time_base.den/stream->codec->time_base.num);
        fprintf(stderr, "Saved chunk with STB: %d/%d (1/=%2.2f)\n", stream->time_base.num, stream->time_base.den, (float)stream->time_base.den/stream->time_base.num);
    }

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

int write_avstream_chunk_as_cc_to_memory(AVCodecContext *codec_ref, AVRational stream_time_base, AVRational stream_frame_rate, uint8_t **unallocd_buffer, int *size){
    
    return 0;
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

int read_avpacket_chunk_from_memory(uint8_t *buf, size_t buf_size, AVPacket *pkt){
    

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
    
    ret = tpl_load(tn, TPL_MEM|TPL_EXCESS_OK, buf, buf_size);
    if(ret < 0)
        return ret;
    
    av_init_packet(pkt);
    
    tpl_unpack(tn,0);
    
    pkt->size = data.sz;
    pkt->data = data.addr;
    
    pkt->destruct = av_destruct_packet;
    
    tpl_free(tn);
    
    if(DEBUG_PRINT)
        fprintf(stderr, "TPL pkt read: s=%d size=%d, dts=%lld, pts=%lld, duration=%d\n", pkt->stream_index, pkt->size, pkt->dts, pkt->pts, pkt->duration);
    
    return ret;
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

    if(DEBUG)
        //fprintf(stderr, "TPL pkt write: s=%d size=%d, dts=%lld, pts=%lld, duration=%d\n", pkt->stream_index, pkt->size, pkt->dts, pkt->pts, pkt->duration);
    
    if(DEBUG && pkt->side_data_elems > 0) 
        fprintf(stderr, "Output pkt:s=%d,ts=%lld was missing some side data.\n", pkt->stream_index, pkt->dts); 
    
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

typedef struct TPLImageRefList {
    tpl_gather_t *gt;
    TPLImageRef *list;
    size_t size;
    size_t capacity;
} TPLImageRefList;

#define TPL_REF_LIST_START_CAP 5000
#define TPL_REF_LIST_INCR 1000

static int tpl_gather_image_list_callback(void *img, size_t sz, void *list_ref_opaque) {
    TPLImageRefList *list_ref = (TPLImageRefList*)list_ref_opaque;
    
    if(list_ref->size+1 >= list_ref->capacity)
    {
        // We need some more space
        list_ref->list = realloc(list_ref->list, sizeof(TPLImageRef) * (list_ref->capacity + TPL_REF_LIST_INCR));
        list_ref->capacity += TPL_REF_LIST_INCR;
    }
    
    // Add this entry to the list.
    list_ref->list[list_ref->size] = (TPLImageRef){img, sz};
    list_ref->size +=1;
    
    return 0;
}

int tpl_gather_image_list(uint8_t *data, size_t size, TPLImageRef **list, size_t *list_size){
    int rc = 0;
    TPLImageRefList list_ref;
    list_ref.gt = NULL;
    list_ref.capacity = TPL_REF_LIST_START_CAP;
    list_ref.size = 0;
    list_ref.list = malloc(sizeof(TPLImageRef) * list_ref.capacity);

    rc = tpl_gather(TPL_GATHER_MEM, data, size, &list_ref.gt, tpl_gather_image_list_callback, &list_ref);
    if(rc >= 0)
    {
        *list = list_ref.list;
        *list_size = list_ref.size;
        return rc;
    }
    else
        return rc;
        
}
