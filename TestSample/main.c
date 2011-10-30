#include "libavcodec/avcodec.h"
#include "libavutil/mathematics.h"
#include "libavutil/imgutils.h"

#include <libavformat/avformat.h>
#include <libavutil/dict.h>

#include <math.h>

#define INBUF_SIZE 4096
#define AUDIO_INBUF_SIZE 20480
#define AUDIO_REFILL_THRESH 4096

/*
 * Audio trancode - from one codec to another.
 */
static void audio_transcode(const char *in_filename, enum CodecID in_codec_id, const char *out_filename, enum CodecID out_codec_id)
{
    // Input declarations.
    AVCodec *in_codec;
    AVCodecContext *in_cc= NULL;
    AVDictionary *in_copts= NULL; // Will be allocated on first use.
    int len;
    FILE *in_file;
    uint8_t inbuf[AUDIO_INBUF_SIZE + FF_INPUT_BUFFER_PADDING_SIZE];
    AVPacket avpkt;
    
    // Output declarations.
    AVCodec *out_codec;
    AVCodecContext *out_cc= NULL;
    AVDictionary *out_copts= NULL; // Will be allocated on first use.
    int frame_size, out_size, outbuf_size;
    short *samples;
    FILE *out_file;
    uint8_t *outbuf;
    
    // ********** Input setup ************
    in_codec = avcodec_find_decoder(in_codec_id);
    if (!in_codec) {
        fprintf(stderr, "input codec not found\n");
        exit(1);
    }
    
    in_cc = avcodec_alloc_context3(in_codec);
    
    /* put sample parameters */
    in_cc->bit_rate = 64000;
    in_cc->sample_rate = 44100;
    in_cc->channels = 2;
    in_cc->sample_fmt = AV_SAMPLE_FMT_S16;
    
    if (avcodec_open2(in_cc, in_codec, &in_copts) < 0) {
        fprintf(stderr, "could not open input codec\n");
        exit(1);
    }
    
    in_file = fopen(in_filename, "rb");
    if (!in_file) {
        fprintf(stderr, "could not open input %s\n", in_filename);
        exit(1);
    }
    
    
    // ********** Output setup ************
    
    out_codec = avcodec_find_encoder(out_codec_id);
    if (!in_codec) {
        fprintf(stderr, "ouput codec not found\n");
        exit(1);
    }
    
    out_cc = avcodec_alloc_context3(out_codec);
    
    /* put sample parameters */
    out_cc->bit_rate = 64000;
    out_cc->sample_rate = 44100;
    out_cc->channels = 2;
    out_cc->sample_fmt = AV_SAMPLE_FMT_S16;
    
    if (avcodec_open2(out_cc, out_codec, &out_copts) < 0) {
        fprintf(stderr, "could not open ouput codec\n");
        exit(1);
    }

    av_init_packet(&avpkt);
    
    outbuf = malloc(AVCODEC_MAX_AUDIO_FRAME_SIZE);
    
    out_file = fopen(out_filename, "wb");
    if (!out_file) {
        fprintf(stderr, "could not open output %s\n", in_filename);
        exit(1);
    }
    
    frame_size = out_cc->frame_size;
    samples = malloc(frame_size * 2 * out_cc->channels);
    outbuf_size = 10000;
    outbuf = malloc(outbuf_size);
    
    // ******** Transcode loop **********
    
    /* decode until eof */
    avpkt.data = inbuf;
    avpkt.size = (int)fread(inbuf, 1, AUDIO_INBUF_SIZE, in_file);
    
    while (avpkt.size > 0) {
        out_size = AVCODEC_MAX_AUDIO_FRAME_SIZE;
        
        len = avcodec_decode_audio3(in_cc, samples, &out_size, &avpkt);
        
        if (len < 0) {
            fprintf(stderr, "Error while decoding\n");
            exit(1);
        }
        if (out_size > 0) {
            // if we have something here, then samples should have the raw decoded sound.
            // use the encoder to output this into outbuf, then write to the disk.
            out_size = avcodec_encode_audio(out_cc, outbuf, outbuf_size, samples);
            fwrite(outbuf, 1, out_size, out_file);
        }
        
        avpkt.size -= len;
        avpkt.data += len;
        
        if (avpkt.size < AUDIO_REFILL_THRESH) {
            /* Refill the input buffer, to avoid trying to decode
             * incomplete frames. Instead of this, one could also use
             * a parser, or use a proper container format through
             * libavformat. */
            memmove(inbuf, avpkt.data, avpkt.size);
            avpkt.data = inbuf;
            len = (int)fread(avpkt.data + avpkt.size, 1, AUDIO_INBUF_SIZE - avpkt.size, in_file);
            if (len > 0)
                avpkt.size += len;
        }
    }
    
    // ***** Tidy up ******
    fclose(in_file);
    fclose(out_file);
    
    free(outbuf);
    free(samples);
    
    avcodec_close(in_cc);
    av_free(in_cc);
    
    avcodec_close(out_cc);
    av_free(out_cc);
}


/*
 * Audio encoding example
 */
static void audio_encode(const char *filename, enum CodecID codec_id)
{
    AVCodec *codec;
    AVCodecContext *c= NULL;
    AVDictionary *copts= NULL; // Will be allocated on first use.
    int frame_size, i, j, out_size, outbuf_size;
    FILE *f;
    short *samples;
    float t, tincr, note;
    uint8_t *outbuf;
    
    printf("Audio encoding\n");
    
    /* find the MP2 encoder */
    codec = avcodec_find_encoder(codec_id);
    if (!codec) {
        fprintf(stderr, "codec not found\n");
        exit(1);
    }
    
    c = avcodec_alloc_context3(codec);
    
    /* put sample parameters */
    c->bit_rate = 64000;
    c->sample_rate = 44100;
    c->channels = 2;
    c->sample_fmt = AV_SAMPLE_FMT_S16;
    
    /* open it */
    if (avcodec_open2(c, codec, &copts) < 0) {
        fprintf(stderr, "could not open codec\n");
        exit(1);
    }
    
    /* the codec gives us the frame size, in samples */
    frame_size = c->frame_size;
    samples = malloc(frame_size * 2 * c->channels);
    outbuf_size = 10000;
    outbuf = malloc(outbuf_size);
    
    f = fopen(filename, "wb");
    if (!f) {
        fprintf(stderr, "could not open %s\n", filename);
        exit(1);
    }
    
    /* encode a single tone sound */
    t = 0;
    note = 261.626;
    for(i=0;i<100;i++) {
        tincr = 2 * M_PI * note / c->sample_rate;
        for(j=0;j<frame_size;j++) {
            samples[2*j] = (int)(sin(t) * 10000);
            samples[2*j+1] = samples[2*j];
            t += tincr;
        }
        /* encode the samples */
        out_size = avcodec_encode_audio(c, outbuf, outbuf_size, samples);
        fwrite(outbuf, 1, out_size, f);
        
        if((i % 10) == 0) 
                note *= 2;
    }
    fclose(f);
    free(outbuf);
    free(samples);
    
    avcodec_close(c);
    av_free(c);
}

/*
 * Audio decoding.
 */
static void audio_decode(const char *filename, enum CodecID codec_id, const char *outfilename)
{
    AVCodec *codec;
    AVCodecContext *c= NULL;
    AVDictionary *copts= NULL; // Will be allocated on first use.
    int out_size, len;
    FILE *f, *outfile;
    uint8_t *outbuf;
    uint8_t inbuf[AUDIO_INBUF_SIZE + FF_INPUT_BUFFER_PADDING_SIZE];
    AVPacket avpkt;
    
    av_init_packet(&avpkt);
    
    printf("Audio decoding\n");
    
    /* find the mpeg audio decoder */
    codec = avcodec_find_decoder(codec_id);
    if (!codec) {
        fprintf(stderr, "codec not found\n");
        exit(1);
    }
    
    c = avcodec_alloc_context3(codec);
    
    /* open it */
    if (avcodec_open2(c, codec, &copts) < 0) {
        fprintf(stderr, "could not open codec\n");
        exit(1);
    }
    
    outbuf = malloc(AVCODEC_MAX_AUDIO_FRAME_SIZE);
    
    f = fopen(filename, "rb");
    if (!f) {
        fprintf(stderr, "could not open %s\n", filename);
        exit(1);
    }
    outfile = fopen(outfilename, "wb");
    if (!outfile) {
        av_free(c);
        exit(1);
    }
    
    /* decode until eof */
    avpkt.data = inbuf;
    avpkt.size = (int)fread(inbuf, 1, AUDIO_INBUF_SIZE, f);
    
    while (avpkt.size > 0) {
        out_size = AVCODEC_MAX_AUDIO_FRAME_SIZE;
        len = avcodec_decode_audio3(c, (short *)outbuf, &out_size, &avpkt);
        if (len < 0) {
            fprintf(stderr, "Error while decoding\n");
            exit(1);
        }
        if (out_size > 0) {
            /* if a frame has been decoded, output it */
            fwrite(outbuf, 1, out_size, outfile);
        }
        avpkt.size -= len;
        avpkt.data += len;
        if (avpkt.size < AUDIO_REFILL_THRESH) {
            /* Refill the input buffer, to avoid trying to decode
             * incomplete frames. Instead of this, one could also use
             * a parser, or use a proper container format through
             * libavformat. */
            memmove(inbuf, avpkt.data, avpkt.size);
            avpkt.data = inbuf;
            len = (int)fread(avpkt.data + avpkt.size, 1, AUDIO_INBUF_SIZE - avpkt.size, f);
            if (len > 0)
                avpkt.size += len;
        }
    }
    
    fclose(outfile);
    fclose(f);
    free(outbuf);
    
    avcodec_close(c);
    av_free(c);
}

/*
 * Video encoding example
 */
static void video_encode(const char *filename)
{
    AVCodec *codec;
    AVCodecContext *c= NULL;
    AVDictionary *copts= NULL; // Will be allocated on first use.
    int i, out_size, x, y, outbuf_size;
    FILE *f;
    AVFrame *picture;
    uint8_t *outbuf;
    
    printf("Video encoding\n");
    
    /* find the mpeg1 video encoder */
    codec = avcodec_find_encoder(CODEC_ID_MPEG1VIDEO);
    if (!codec) {
        fprintf(stderr, "codec not found\n");
        exit(1);
    }
    
    c = avcodec_alloc_context3(codec);
    picture= avcodec_alloc_frame();
    
    /* put sample parameters */
    c->bit_rate = 400000;
    /* resolution must be a multiple of two */
    c->width = 352;
    c->height = 288;
    /* frames per second */
    c->time_base= (AVRational){1,25};
    c->gop_size = 10; /* emit one intra frame every ten frames */
    c->max_b_frames=1;
    c->pix_fmt = PIX_FMT_YUV420P;
    
    /* open it */
    if (avcodec_open2(c, codec, &copts) < 0) {
        fprintf(stderr, "could not open codec\n");
        exit(1);
    }
    
    f = fopen(filename, "wb");
    if (!f) {
        fprintf(stderr, "could not open %s\n", filename);
        exit(1);
    }
    
    /* alloc image and output buffer */
    outbuf_size = 100000;
    outbuf = malloc(outbuf_size);
    
    /* the image can be allocated by any means and av_image_alloc() is
     * just the most convenient way if av_malloc() is to be used */
    av_image_alloc(picture->data, picture->linesize,
                   c->width, c->height, c->pix_fmt, 1);
    
    /* encode 1 second of video */
    for(i=0;i<25*100;i++) {
        fflush(stdout);
        /* prepare a dummy image */
        /* Y */
        for(y=0;y<c->height;y++) {
            for(x=0;x<c->width;x++) {
                picture->data[0][y * picture->linesize[0] + x] = x + y + i * 3;
            }
        }
        
        /* Cb and Cr */
        for(y=0;y<c->height/2;y++) {
            for(x=0;x<c->width/2;x++) {
                picture->data[1][y * picture->linesize[1] + x] = 128 + y + i * 2;
                picture->data[2][y * picture->linesize[2] + x] = 64 + x + i * 5;
            }
        }
        
        /* encode the image */
        out_size = avcodec_encode_video(c, outbuf, outbuf_size, picture);
        printf("encoding frame %3d (size=%5d)\n", i, out_size);
        fwrite(outbuf, 1, out_size, f);
    }
    
    /* get the delayed frames */
    for(; out_size; i++) {
        fflush(stdout);
        
        out_size = avcodec_encode_video(c, outbuf, outbuf_size, NULL);
        printf("write frame %3d (size=%5d)\n", i, out_size);
        fwrite(outbuf, 1, out_size, f);
    }
    
    /* add sequence end code to have a real mpeg file */
    outbuf[0] = 0x00;
    outbuf[1] = 0x00;
    outbuf[2] = 0x01;
    outbuf[3] = 0xb7;
    fwrite(outbuf, 1, 4, f);
    fclose(f);
    free(outbuf);
    
    avcodec_close(c);
    av_free(c);
    av_free(picture->data[0]);
    av_free(picture);
    printf("\n");
}

/*
 * Video decoding example
 */

static void pgm_save(unsigned char *buf, int wrap, int xsize, int ysize,
                     char *filename)
{
    FILE *f;
    int i;
    
    f=fopen(filename,"w");
    fprintf(f,"P5\n%d %d\n%d\n",xsize,ysize,255);
    for(i=0;i<ysize;i++)
        fwrite(buf + i * wrap,1,xsize,f);
    fclose(f);
}

static void video_decode(const char *outfilename, const char *filename)
{
    AVCodec *codec;
    AVCodecContext *c= NULL;
    AVDictionary *copts= NULL; // Will be allocated on first use.
    int frame, got_picture, len;
    FILE *f;
    AVFrame *picture;
    uint8_t inbuf[INBUF_SIZE + FF_INPUT_BUFFER_PADDING_SIZE];
    char buf[1024];
    AVPacket avpkt;
    
    av_init_packet(&avpkt);
    
    /* set end of buffer to 0 (this ensures that no overreading happens for damaged mpeg streams) */
    memset(inbuf + INBUF_SIZE, 0, FF_INPUT_BUFFER_PADDING_SIZE);
    
    printf("Video decoding\n");
    
    /* find the mpeg1 video decoder */
    codec = avcodec_find_decoder(CODEC_ID_MPEG1VIDEO);
    if (!codec) {
        fprintf(stderr, "codec not found\n");
        exit(1);
    }
    
    c = avcodec_alloc_context3(codec);
    picture= avcodec_alloc_frame();
    
    if(codec->capabilities&CODEC_CAP_TRUNCATED)
        c->flags|= CODEC_FLAG_TRUNCATED; /* we do not send complete frames */
    
    /* For some codecs, such as msmpeg4 and mpeg4, width and height
     MUST be initialized there because this information is not
     available in the bitstream. */
    
    /* open it */
    if (avcodec_open2(c, codec, &copts) < 0) {
        fprintf(stderr, "could not open codec\n");
        exit(1);
    }
    
    /* the codec gives us the frame size, in samples */
    
    f = fopen(filename, "rb");
    if (!f) {
        fprintf(stderr, "could not open %s\n", filename);
        exit(1);
    }
    
    frame = 0;
    for(;;) {
        avpkt.size = (int)fread(inbuf, 1, INBUF_SIZE, f);
        if (avpkt.size == 0)
            break;
        
        /* NOTE1: some codecs are stream based (mpegvideo, mpegaudio)
         and this is the only method to use them because you cannot
         know the compressed data size before analysing it.
         
         BUT some other codecs (msmpeg4, mpeg4) are inherently frame
         based, so you must call them with all the data for one
         frame exactly. You must also initialize 'width' and
         'height' before initializing them. */
        
        /* NOTE2: some codecs allow the raw parameters (frame size,
         sample rate) to be changed at any frame. We handle this, so
         you should also take care of it */
        
        /* here, we use a stream based decoder (mpeg1video), so we
         feed decoder and see if it could decode a frame */
        avpkt.data = inbuf;
        while (avpkt.size > 0) {
            len = avcodec_decode_video2(c, picture, &got_picture, &avpkt);
            if (len < 0) {
                fprintf(stderr, "Error while decoding frame %d\n", frame);
                exit(1);
            }
            if (got_picture) {
                printf("saving frame %3d\n", frame);
                fflush(stdout);
                
                /* the picture is allocated by the decoder. no need to
                 free it */
                snprintf(buf, sizeof(buf), outfilename, frame);
                pgm_save(picture->data[0], picture->linesize[0],
                         c->width, c->height, buf);
                frame++;
            }
            avpkt.size -= len;
            avpkt.data += len;
        }
    }
    
    /* some codecs, such as MPEG, transmit the I and P frame with a
     latency of one frame. You must do the following to have a
     chance to get the last frame of the video */
    avpkt.data = NULL;
    avpkt.size = 0;
    len = avcodec_decode_video2(c, picture, &got_picture, &avpkt);
    if (got_picture) {
        printf("saving last frame %3d\n", frame);
        fflush(stdout);
        
        /* the picture is allocated by the decoder. no need to
         free it */
        snprintf(buf, sizeof(buf), outfilename, frame);
        pgm_save(picture->data[0], picture->linesize[0],
                 c->width, c->height, buf);
        frame++;
    }
    
    fclose(f);
    
    avcodec_close(c);
    av_free(c);
    av_free(picture);
    printf("\n");
}

static int print_metadata(const char *filename)
{
    AVFormatContext *fmt_ctx = NULL;
    AVDictionaryEntry *tag = NULL;
    
    int ret;
    
    av_register_all();
    
    if ((ret = avformat_open_input(&fmt_ctx, filename, NULL, NULL)))
        return ret;
    
    while ((tag = av_dict_get(fmt_ctx->metadata, "", tag, AV_DICT_IGNORE_SUFFIX)))
        printf("%s=%s\n", tag->key, tag->value);
    
    avformat_free_context(fmt_ctx);
    return 0;

}

static int keyframe_analysis(const char *filename, unsigned int demux)
{
    
    AVFormatContext *fmt_ctx = NULL;
    AVDictionaryEntry *t;
    int err, i;
    
    if ((err = avformat_open_input(&fmt_ctx, filename, NULL, NULL)) < 0) {
        printf("Failed to open file %s, error %d", filename, err);
        return err;
    }
    
    if ((t = av_dict_get(NULL, "", NULL, AV_DICT_IGNORE_SUFFIX))) {
        av_log(NULL, AV_LOG_ERROR, "Option %s not found.\n", t->key);
        return AVERROR_OPTION_NOT_FOUND;
    }
    
    
    /* fill the streams in the format context */
    if ((err = avformat_find_stream_info(fmt_ctx, NULL)) < 0) {
        printf("Failed to open streams in file %s, error %d", filename, err);
        return err;
    }
    
    /* bind a decoder to each input stream */
    for (i = 0; i < fmt_ctx->nb_streams; i++) {
        AVStream *stream = fmt_ctx->streams[i];
        AVCodec *codec;
        
        if (!(codec = avcodec_find_decoder(stream->codec->codec_id))) {
            fprintf(stderr, "Unsupported codec with id %d for input stream %d\n",
                    stream->codec->codec_id, stream->index);
        } else if (avcodec_open2(stream->codec, codec, NULL) < 0) {
            fprintf(stderr, "Error while opening codec for input stream %d\n",
                    stream->index);
        }
    }
    
    av_dump_format(fmt_ctx, 0, filename, 0);
     
    /* Go through each of the streams and count up their key frames */
    {
        int count_total[fmt_ctx->nb_streams];
        int count_kf[fmt_ctx->nb_streams];
        
        AVOutputFormat output_formats[fmt_ctx->nb_streams];
        AVFormatContext output_format_context[fmt_ctx->nb_streams];
        AVStream output_streams[fmt_ctx->nb_streams];
        FILE output_files[fmt_ctx->nb_streams];
        
        // Zero the arrays, and open up the files.
        for (i = 0; i < fmt_ctx->nb_streams; i++) {
            count_total[i] = 0;
            count_kf[i] = 0;
            
            if(demux != 0){
                FILE *file;
                char *stream_filename;
                unsigned long stream_filename_size = strlen(filename) + 10;
                
                stream_filename = calloc(stream_filename_size, sizeof(char));
                sprintf(stream_filename, "%s.%d.mp4", filename, i);
                
                file = fopen(stream_filename, "wb");
                
                free(stream_filename);
                
                if(file != NULL)
                    output_files[i] = *file;
                else
                {
                    printf("Could not open %s for output of steram.", stream_filename);
                    return -1;
                }
            }
            
            
        }
        
        // Read in the data, and demux it.
        {
            AVPacket pkt;
            av_init_packet(&pkt);
            
            while (!av_read_frame(fmt_ctx, &pkt))
            {
                // Add the totals.
                count_total[pkt.stream_index] += 1;
                
                if(pkt.flags & AV_PKT_FLAG_KEY)
                    count_kf[pkt.stream_index] += 1;
                
                // Write out the streams.
                if(demux !=0)
                    fwrite(pkt.data, 1, pkt.size, &output_files[pkt.stream_index]);
            }
        }
        
        // Print out the totals for each of the streams.
        for (i = 0; i < fmt_ctx->nb_streams; i++) {
            AVStream *stream = fmt_ctx->streams[i];
            printf("Stream %d:\n", i);
            printf("    Total frames: %d\n", count_total[i]);
            printf("    Key frames: %d\n", count_kf[i]);
            printf("    Non-Key frames: %d\n", count_total[i]-count_kf[i]);
            if((count_total[i]-count_kf[i]) > 0)
                printf("    Average GOP size (frames): %.2f\n", ((double)count_total[i])/count_kf[i]);
            if(stream->r_frame_rate.den)
                printf("    Average GOP size (sec): %.2f\n", (((double)count_total[i])/count_kf[i]) / (stream->r_frame_rate.num/stream->r_frame_rate.den) );
        }
        
        if(demux != 0)
        {
            for (i = 0; i < fmt_ctx->nb_streams; i++) {
                fclose(&output_files[i]);
            } 
        }
    }
    
    
    av_close_input_file(fmt_ctx);
    
    return 0;
}

int main(int argc, char **argv)
{
    av_register_all();
    avcodec_register_all();

    keyframe_analysis("/Users/tom/Desktop/Test.avi", 0);
    
    //audio_encode("/tmp/test.mp2", CODEC_ID_MP2);
    //audio_transcode("/tmp/test.mp2", CODEC_ID_MP2, "/tmp/test.mp3", CODEC_ID_MP3);
    
    //audio_decode("/tmp/test.mp3", CODEC_ID_MP3, "/tmp/test.sw");
        
    //video_encode("/tmp/test.mpg");
    //keyframe_analysis("/tmp/test.mpg");
    
    //video_decode("/tmp/test%d.pgm", "/tmp/test.mpg");
    
    return 0;
}
