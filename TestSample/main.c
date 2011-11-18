#include "ffmpeg_tpl.h"

#include "libavcodec/avcodec.h"
#include "libavutil/mathematics.h"
#include "libavutil/imgutils.h"
#include "libavutil/dict.h"
#include "libavformat/avformat.h"

#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <math.h>
#include <errno.h>
#include <string.h>

#define INBUF_SIZE 4096
#define AUDIO_INBUF_SIZE 20480
#define AUDIO_REFILL_THRESH 4096

static int file_exists(char *fileName)
{   
    struct stat buf;   
    int ret = stat(fileName, &buf);
    if (ret == 0)return 1;     
    else         return 0;       
}


// Counts up from zero looking for files like: <filename_base>.<n>
// Returns the number found.
static int count_files_with_base(char *filename_base, char ***filenames)
{
    int counter = 0;
    char *test_filename;
    unsigned long str_size = strlen(filename_base) + 5; // (this goes up to 9999 streams!!! + \0)

    // Alloc the first one, then see if it exists.
    test_filename = calloc(1, str_size);
    sprintf(test_filename, "%s.%d", filename_base, counter);
    
    while(file_exists(test_filename))
    {
        // Save the sprintf buffer.
        *filenames = realloc(*filenames, str_size * sizeof(char*));
        (*filenames)[counter] = test_filename;
     
        // Inc the counter.
        counter++;
        
        // Prep for next time.
        test_filename = calloc(1, str_size);
        sprintf(test_filename, "%s.%d", filename_base, counter);
    }
    
    free(test_filename); // This will be for the name that wasn't there.
    return counter;
}

static AVStream *add_copy_stream(AVFormatContext *os, AVStream *stream)
{
    AVCodecContext *codec, *icodec;
    AVStream *new_stream;
    
    icodec = stream->codec;
    new_stream = av_new_stream(os, 0);
    if (!new_stream) {
        fprintf(stderr, "Could not alloc stream\n");
        exit(1);
    }
    
    new_stream->stream_copy = -1;
    codec = new_stream->codec;
    new_stream->disposition = stream->disposition;
    codec->bits_per_raw_sample= icodec->bits_per_raw_sample;
    codec->chroma_sample_location = icodec->chroma_sample_location;

    if (new_stream->stream_copy) {
        uint64_t extra_size = (uint64_t)icodec->extradata_size + FF_INPUT_BUFFER_PADDING_SIZE;
        
        if (extra_size > INT_MAX) {
            return NULL;
        }
        
        /* if stream_copy is selected, no need to decode or encode */
        codec->codec_id = icodec->codec_id;
        codec->codec_type = icodec->codec_type;
        
        if(!codec->codec_tag){
            if(   !os->oformat->codec_tag
               || av_codec_get_id (os->oformat->codec_tag, icodec->codec_tag) == codec->codec_id
               || av_codec_get_tag(os->oformat->codec_tag, icodec->codec_id) <= 0)
                codec->codec_tag = icodec->codec_tag;
        }
        
        codec->bit_rate = icodec->bit_rate;
        codec->rc_max_rate    = icodec->rc_max_rate;
        codec->rc_buffer_size = icodec->rc_buffer_size;
        codec->extradata= av_mallocz(extra_size);
        if (!codec->extradata) {
            return NULL;
        }
        memcpy(codec->extradata, icodec->extradata, icodec->extradata_size);
        codec->extradata_size = icodec->extradata_size;
        
        codec->time_base = stream->time_base;
        av_reduce(&codec->time_base.num, &codec->time_base.den, codec->time_base.num, codec->time_base.den, INT_MAX);
        
        switch(codec->codec_type) {
            case AVMEDIA_TYPE_AUDIO:
                codec->channel_layout = icodec->channel_layout;
                codec->sample_rate = icodec->sample_rate;
                codec->channels = icodec->channels;
                codec->frame_size = icodec->frame_size;
                codec->audio_service_type = icodec->audio_service_type;
                codec->block_align= icodec->block_align;
                if(codec->block_align == 1 && codec->codec_id == CODEC_ID_MP3)
                    codec->block_align= 0;
                if(codec->codec_id == CODEC_ID_AC3)
                    codec->block_align= 0;
                break;
            case AVMEDIA_TYPE_VIDEO:
                codec->pix_fmt = icodec->pix_fmt;
                codec->width = icodec->width;
                codec->height = icodec->height;
                codec->has_b_frames = icodec->has_b_frames;
                if (!codec->sample_aspect_ratio.num) {
                    codec->sample_aspect_ratio =
                    new_stream->sample_aspect_ratio =
                        stream->sample_aspect_ratio.num ? stream->sample_aspect_ratio :
                        stream->codec->sample_aspect_ratio.num ?
                        stream->codec->sample_aspect_ratio : (AVRational){0, 1};
                }
                break;
            case AVMEDIA_TYPE_SUBTITLE:
                codec->width = icodec->width;
                codec->height = icodec->height;
                break;
            case AVMEDIA_TYPE_DATA:
                break;
            default:
                abort();
        }
    } 
    
    return new_stream;
}

static int split_streams(const char *filename, const char *out_filename_base)
{
    
    AVFormatContext *fmt_ctx = NULL;
    int err, i;
    
    if ((err = avformat_open_input(&fmt_ctx, filename, NULL, NULL)) < 0) {
        printf("Failed to open file %s, error %d", filename, err);
        return err;
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
            fprintf(stderr, "Unsupported codec with id %d for input stream %d\n", stream->codec->codec_id, stream->index);
        } else if (avcodec_open2(stream->codec, codec, NULL) < 0) {
            fprintf(stderr, "Error while opening codec for input stream %d\n", stream->index);
        }
    }
    
    av_dump_format(fmt_ctx, 0, filename, 0);
     
    /* Go through each of the streams and init everything for the remux */
    int output_fds[fmt_ctx->nb_streams];
    int count_total[fmt_ctx->nb_streams];
    int count_kf[fmt_ctx->nb_streams];
    
    // Zero the arrays, and open up the files.
    for (i = 0; i < fmt_ctx->nb_streams; i++) {
        count_total[i] = 0;
        count_kf[i] = 0;
        output_fds[i] = -1;
            
        if(fmt_ctx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO ||
           fmt_ctx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO)
        {
            char *stream_filename;
            unsigned long stream_filename_size = strlen(out_filename_base) + 10;
            
            stream_filename = calloc(stream_filename_size, sizeof(char));
            sprintf(stream_filename, "%s.%d", out_filename_base, i);
            
            // Open the files.
            if ((output_fds[i] = open(stream_filename, O_WRONLY | O_CREAT | O_TRUNC,  S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH)) < 0) {
                fprintf(stderr, "Could not open '%s': %s\n", stream_filename, strerror(errno));
                return -1;
            }
            
            free(stream_filename);
            
            // Write out the stream meta data to the file.
            write_avstream_chunk_to_file(fmt_ctx->streams[i], output_fds[i]);
        }
    }
    
    
    // Read in the data, and demux it.
    AVPacket pkt;
    av_init_packet(&pkt);
    
    while (!av_read_frame(fmt_ctx, &pkt))
    {
        
        // Add the totals.
        count_total[pkt.stream_index] += 1;
        
        if(pkt.flags & AV_PKT_FLAG_KEY)
            count_kf[pkt.stream_index] += 1;
        
        // Write out the streams.
        if(output_fds[pkt.stream_index] != -1)
        {
            printf("Split packet for stream %d (dts=%lld, pts=%lld, size=%d)\n", pkt.stream_index, pkt.dts, pkt.pts, pkt.size);
            
            // For the stream we have a packet for, write out the contents 
            // to the corresponding stream output file.
            write_avpacket_chunk_to_file(&pkt, output_fds[pkt.stream_index]); 
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
    
    for (i = 0; i < fmt_ctx->nb_streams; i++) {
        if(output_fds[i] != -1)
            close(output_fds[i]);
    } 
    
    av_close_input_file(fmt_ctx);
    
    return 0;
}

static int merge_streams(const char *filename_base, const char *output_filename)
{
    int i, err;
    AVFormatContext *output_format_context = NULL;
    
    // Create an output file AVFormatContext and open the file for writing to the filesystem.
    avformat_alloc_output_context2(&output_format_context, NULL, NULL, output_filename);
    if (!&output_format_context) {
        printf("Unable to create output context for %s", output_filename);
        return -1;
    }
    if (avio_open(&output_format_context->pb, output_filename, AVIO_FLAG_WRITE) < 0) {
        fprintf(stderr, "Could not open '%s'\n", output_filename);
        return -1;
    }
    
    // Count up the streams from the filesystem using the the filename base, and create and array of input AVFormatContexts from the files.
    // Also copy the stream metadata from each of the input files into one main file (just use the order that they come in for now).
    char **input_filenames = NULL;
    int nb_streams = count_files_with_base((char *)filename_base, &input_filenames);
    int input_fds[nb_streams];
    int input_fds_ret[nb_streams];
    
    for (i = 0; i < nb_streams; i++) {
        
        /* fill the streams in the format context */
        input_fds[i] = open(input_filenames[i], O_RDONLY);
        if (input_fds[i] < 0) {
            printf("Failed to open streams in file %s, error %s\n", input_filenames[i], strerror(errno));
            return err;
        }
    
        // Read in the stream from the front of the file.
        AVStream *stream;
        read_avstream_chunk_from_file(output_format_context, input_fds[i], &stream);
        
        // Open the codecs and 
        AVCodec *codec;

        if (!(codec = avcodec_find_decoder(stream->codec->codec_id))) {
            fprintf(stderr, "Unsupported codec with id %d for input stream %d\n", stream->codec->codec_id, stream->index);
        } else if (avcodec_open2(stream->codec, codec, NULL) < 0) {
            fprintf(stderr, "Error while opening codec for input stream %d\n", stream->index);
        }
    }
    
    // Write out any headers for the output files.
    if(avformat_write_header(output_format_context, NULL) != 0)
    {
        fprintf(stderr, "Failed to write header.\n");
        return -1;
    }
    
    // Go through and read from each stream over and over, taking the packets and av_interleaved_write_frame to the correct stream in the output to merge the files again.
    AVPacket pkt;
    int current_fmt_ctx = 0; // TODO: This is a bit minging and assumes that the inputs only have one stream - fix me!
    av_init_packet(&pkt);
    
    // Init the returns.
    for (i = 0; i < nb_streams; i++) {
        input_fds_ret[i] = 0;
    }
    
    // Main loop - this is an awful hacky mess. Fix it please. 
    for(;;)
    {
        // Read in some data.
        if(!input_fds_ret[current_fmt_ctx])
            input_fds_ret[current_fmt_ctx] = read_avpacket_chunk_from_file(input_fds[current_fmt_ctx], &pkt);
        
        // If we got something, process it.
        if(!input_fds_ret[current_fmt_ctx])
        {
            // For the stream we have a packet for, write out the contents to the corresponding stream output file.
            printf("Writing packet to merge for stream %d (dts=%lld, pts=%lld, size=%d)\n", pkt.stream_index, pkt.dts, pkt.pts, pkt.size);
            av_interleaved_write_frame(output_format_context, &pkt);
        }
        
        // Increment the current_fmt_ctx
        if(current_fmt_ctx == nb_streams-1)
            current_fmt_ctx = 0;
        else
            current_fmt_ctx++;
        
        // Break if all of the fds are empty.
        int empty_count = 0;
        for (i = 0; i < nb_streams; i++) {
            if(input_fds_ret[i] == -1)
                empty_count++;
        }
        if (empty_count == nb_streams) {
            break;
        }
    }
    
    // Write the output file trailers.
    av_write_trailer(output_format_context);
    
    // Free up stuff.
    for(i = 0; i < nb_streams; i++)
    {
        close(input_fds[i]);
        free(input_filenames[i]);
    }
    free(input_filenames);
    return 0;
}

int main(int argc, char **argv)
{
    av_register_all();
    avcodec_register_all();

    //split_streams("/Users/tom/Test.mp4", "/Users/tom/Test.mp4");
    merge_streams("/Users/tom/Test.mp4", "/Users/tom/Test-Merged.mp4");
    
    //split_streams("/Users/tom/Test.avi", "/Users/tom/Test.avi");
    merge_streams("/Users/tom/Test.avi", "/Users/tom/Test-Merged.avi");
    
    //split_streams("/Users/tom/Test.mkv", "/Users/tom/Test.mkv");
    //merge_streams("/Users/tom/Test.mkv", "/Users/tom/Test-Merged.mkv");
    
    return 0;
}