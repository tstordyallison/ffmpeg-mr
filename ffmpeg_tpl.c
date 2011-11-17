#include "tpl.h"
#include "ffmpeg_tpl.h"
#include <libavformat/avformat.h>


// This is the format definition for the AVStream that we store.
// Most of the data we store for a stream, is really the codec context.
// We don't store any of the transient information about the stream - this is not what we are bothered about.
// This takes most of its def. from the copy_stream method found in ffmpeg.c
#define AVSTREAM_TPL_FORMAT "iiiiiiiiiviiiiiIiiiiiiiiiiiiB"
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
 v  =   unsigned int codec_tag;
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


// This is the format definition for the AVPackets that we store.
#define AVPACKET_TPL_FORMAT "IIiiIiB"
/*
 
I = int64_t pts;
I = int64_t dts;
i = int   flags;
i = int   duration;
I = int64_t convergence_duration;
i = int   stream_index;
B = uint8_t *data/size;

*/


int read_avstream_chunk_from_file(int fd, AVStream *stream){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &stream->disposition,
                 &stream->time_base.num,
                 &stream->time_base.den,
                 &stream->sample_aspect_ratio.num,
                 &stream->sample_aspect_ratio.den,
                 &stream->codec->bits_per_raw_sample,
                 &stream->codec->chroma_sample_location,
                 &stream->codec->codec_id,
                 &stream->codec->codec_type,
                 &stream->codec->codec_tag,
                 &stream->codec->bit_rate,
                 &stream->codec->rc_max_rate,
                 &stream->codec->rc_buffer_size,
                 &stream->codec->time_base.num,
                 &stream->codec->time_base.den,
                 &stream->codec->channel_layout,
                 &stream->codec->sample_rate,
                 &stream->codec->channels,
                 &stream->codec->sample_fmt,
                 &stream->codec->frame_size,
                 &stream->codec->audio_service_type,
                 &stream->codec->block_align,
                 &stream->codec->pix_fmt,
                 &stream->codec->width,
                 &stream->codec->height,
                 &stream->codec->has_b_frames,
                 &stream->codec->sample_aspect_ratio.num,
                 &stream->codec->sample_aspect_ratio.den,
                 &data);
    
    ret = tpl_load(tn, TPL_FD, fd);
    if(ret < 0)
        return ret;
    
    tpl_unpack(tn,0);
    
    stream->codec->extradata_size = data.sz;
    stream->codec->extradata = data.addr;
    
    tpl_free(tn);
    
    return ret;
}

int write_avstream_chunk_to_file(AVStream *stream, int fd){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    data.sz = stream->codec->extradata_size; 
    data.addr = stream->codec->extradata;
    
    tn = tpl_map(AVSTREAM_TPL_FORMAT,
                 &stream->disposition,
                 &stream->time_base.num,
                 &stream->time_base.den,
                 &stream->sample_aspect_ratio.num,
                 &stream->sample_aspect_ratio.den,
                 &stream->codec->bits_per_raw_sample,
                 &stream->codec->chroma_sample_location,
                 &stream->codec->codec_id,
                 &stream->codec->codec_type,
                 &stream->codec->codec_tag,
                 &stream->codec->bit_rate,
                 &stream->codec->rc_max_rate,
                 &stream->codec->rc_buffer_size,
                 &stream->codec->time_base.num,
                 &stream->codec->time_base.den,
                 &stream->codec->channel_layout,
                 &stream->codec->sample_rate,
                 &stream->codec->channels,
                 &stream->codec->sample_fmt,
                 &stream->codec->frame_size,
                 &stream->codec->audio_service_type,
                 &stream->codec->block_align,
                 &stream->codec->pix_fmt,
                 &stream->codec->width,
                 &stream->codec->height,
                 &stream->codec->has_b_frames,
                 &stream->codec->sample_aspect_ratio.num,
                 &stream->codec->sample_aspect_ratio.den,
                 &data);
    
    tpl_pack(tn,0);
    ret = tpl_dump(tn, TPL_FD, fd);
    tpl_free(tn);
    return ret;
}

int read_avpacket_chunk_from_file(int fd, AVPacket *pkt){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    tn = tpl_map(AVPACKET_TPL_FORMAT,
                 &pkt->pts, 
                 &pkt->dts,
                 &pkt->flags, 
                 &pkt->duration,
                 &pkt->convergence_duration,
                 &pkt->stream_index,
                 &data);
    
    ret = tpl_load(tn, TPL_FD, fd);
    if(ret < 0)
        return ret;
    
    tpl_unpack(tn,0);
    
    pkt->size = data.sz;
    pkt->data = data.addr;
    
    tpl_free(tn);
    
    return ret;
}

int write_avpacket_chunk_to_file(AVPacket *pkt, int fd){
    tpl_node *tn;
    tpl_bin data;
    int ret;
    
    data.sz = pkt->size;
    data.addr = &(pkt->data);
    
    tn = tpl_map(AVPACKET_TPL_FORMAT,
                 &pkt->pts, 
                 &pkt->dts,
                 &pkt->flags, 
                 &pkt->duration,
                 &pkt->convergence_duration,
                 &pkt->stream_index,
                 &data);
    
    tpl_pack(tn,0);
    ret = tpl_dump(tn, TPL_FD, fd);
    tpl_free(tn);
    return ret;
}