MapReduce Audio and Video Transcoding
---------------------------------------

See: <https://github.com/tstordyallison/ffmpeg-mr/blob/master/docs/paper/Final%20Paper.pdf?raw=true>

Submitted as part of the degree of Computer Science to the Board of Examiners in the Department of Engineering and Computing Sciences, Durham University in Summer 2012.

Background
-----------
MapReduce is a paradigm for distributing large amounts of data across a cluster of machines and then analysing the data in parallel in a two-stage process taken from the world of functional programming. MapReduce allows for processing to be easily scaled across multiple machines.

Aims
-----
To investigate the feasibility of using the MapReduce paradigm to process video and transcode it into different formats and codecs. Our aim was to achieve a performance improvement over a sequential single machine transcoder, and investigate this improvement as the number of machines used in a cluster is increased.

Method
------
The transcoding problem was formulated for MapReduce using Apache Hadoop, and then it was used in various scenarios to test its feasibility in clusters of up to 20 machines. The performance of our solution as the number of nodes in the cluster is increased was analysed, and we examined how the performance was affected by the size of the input. We also analysed the output of our transcoder in terms of visual quality and compression.

Results
--------
Our implementation of video transcoding on MapReduce shows a performance improvement when large files are used for input, for cluster sizes up to 20. We found input file size to have a considerable effect on our system, larger input reducing overhead and increasing scalability.

Conclusions
------------
Transcoding on MapReduce allows for large video files to be processed on clusters of machines, achieving a performance improvement with respect to the number of machines used. The output is of similar quality and of similar file size to that of a sequential encoder.