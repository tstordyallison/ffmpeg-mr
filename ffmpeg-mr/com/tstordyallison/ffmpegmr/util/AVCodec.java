package com.tstordyallison.ffmpegmr.util;
/**
 * Generated using JNAerator, starting with line offset 52.
 * 
 * Taken from avcodec.h
 * 
 * <i>native declaration : line 2</i><br>
 * enum values
 */
public interface AVCodec {
	/// <i>native declaration : line 3</i>
	public static final int CODEC_ID_NONE = 0;
	/**
	 * video codecs<br>
	 * video codecs<br>
	 * <i>native declaration : line 6</i>
	 */
	public static final int CODEC_ID_MPEG1VIDEO = 1;
	/// <i>native declaration : line 7</i>
	public static final int CODEC_ID_MPEG2VIDEO = 2;
	/**
	 * < preferred ID for MPEG-1/2 video decoding<br>
	 * <i>native declaration : line 8</i>
	 */
	public static final int CODEC_ID_MPEG2VIDEO_XVMC = 3;
	/// <i>native declaration : line 9</i>
	public static final int CODEC_ID_H261 = 4;
	/// <i>native declaration : line 10</i>
	public static final int CODEC_ID_H263 = 5;
	/// <i>native declaration : line 11</i>
	public static final int CODEC_ID_RV10 = 6;
	/// <i>native declaration : line 12</i>
	public static final int CODEC_ID_RV20 = 7;
	/// <i>native declaration : line 13</i>
	public static final int CODEC_ID_MJPEG = 8;
	/// <i>native declaration : line 14</i>
	public static final int CODEC_ID_MJPEGB = 9;
	/// <i>native declaration : line 15</i>
	public static final int CODEC_ID_LJPEG = 10;
	/// <i>native declaration : line 16</i>
	public static final int CODEC_ID_SP5X = 11;
	/// <i>native declaration : line 17</i>
	public static final int CODEC_ID_JPEGLS = 12;
	/// <i>native declaration : line 18</i>
	public static final int CODEC_ID_MPEG4 = 13;
	/// <i>native declaration : line 19</i>
	public static final int CODEC_ID_RAWVIDEO = 14;
	/// <i>native declaration : line 20</i>
	public static final int CODEC_ID_MSMPEG4V1 = 15;
	/// <i>native declaration : line 21</i>
	public static final int CODEC_ID_MSMPEG4V2 = 16;
	/// <i>native declaration : line 22</i>
	public static final int CODEC_ID_MSMPEG4V3 = 17;
	/// <i>native declaration : line 23</i>
	public static final int CODEC_ID_WMV1 = 18;
	/// <i>native declaration : line 24</i>
	public static final int CODEC_ID_WMV2 = 19;
	/// <i>native declaration : line 25</i>
	public static final int CODEC_ID_H263P = 20;
	/// <i>native declaration : line 26</i>
	public static final int CODEC_ID_H263I = 21;
	/// <i>native declaration : line 27</i>
	public static final int CODEC_ID_FLV1 = 22;
	/// <i>native declaration : line 28</i>
	public static final int CODEC_ID_SVQ1 = 23;
	/// <i>native declaration : line 29</i>
	public static final int CODEC_ID_SVQ3 = 24;
	/// <i>native declaration : line 30</i>
	public static final int CODEC_ID_DVVIDEO = 25;
	/// <i>native declaration : line 31</i>
	public static final int CODEC_ID_HUFFYUV = 26;
	/// <i>native declaration : line 32</i>
	public static final int CODEC_ID_CYUV = 27;
	/// <i>native declaration : line 33</i>
	public static final int CODEC_ID_H264 = 28;
	/// <i>native declaration : line 34</i>
	public static final int CODEC_ID_INDEO3 = 29;
	/// <i>native declaration : line 35</i>
	public static final int CODEC_ID_VP3 = 30;
	/// <i>native declaration : line 36</i>
	public static final int CODEC_ID_THEORA = 31;
	/// <i>native declaration : line 37</i>
	public static final int CODEC_ID_ASV1 = 32;
	/// <i>native declaration : line 38</i>
	public static final int CODEC_ID_ASV2 = 33;
	/// <i>native declaration : line 39</i>
	public static final int CODEC_ID_FFV1 = 34;
	/// <i>native declaration : line 40</i>
	public static final int CODEC_ID_4XM = 35;
	/// <i>native declaration : line 41</i>
	public static final int CODEC_ID_VCR1 = 36;
	/// <i>native declaration : line 42</i>
	public static final int CODEC_ID_CLJR = 37;
	/// <i>native declaration : line 43</i>
	public static final int CODEC_ID_MDEC = 38;
	/// <i>native declaration : line 44</i>
	public static final int CODEC_ID_ROQ = 39;
	/// <i>native declaration : line 45</i>
	public static final int CODEC_ID_INTERPLAY_VIDEO = 40;
	/// <i>native declaration : line 46</i>
	public static final int CODEC_ID_XAN_WC3 = 41;
	/// <i>native declaration : line 47</i>
	public static final int CODEC_ID_XAN_WC4 = 42;
	/// <i>native declaration : line 48</i>
	public static final int CODEC_ID_RPZA = 43;
	/// <i>native declaration : line 49</i>
	public static final int CODEC_ID_CINEPAK = 44;
	/// <i>native declaration : line 50</i>
	public static final int CODEC_ID_WS_VQA = 45;
	/// <i>native declaration : line 51</i>
	public static final int CODEC_ID_MSRLE = 46;
	/// <i>native declaration : line 52</i>
	public static final int CODEC_ID_MSVIDEO1 = 47;
	/// <i>native declaration : line 53</i>
	public static final int CODEC_ID_IDCIN = 48;
	/// <i>native declaration : line 54</i>
	public static final int CODEC_ID_8BPS = 49;
	/// <i>native declaration : line 55</i>
	public static final int CODEC_ID_SMC = 50;
	/// <i>native declaration : line 56</i>
	public static final int CODEC_ID_FLIC = 51;
	/// <i>native declaration : line 57</i>
	public static final int CODEC_ID_TRUEMOTION1 = 52;
	/// <i>native declaration : line 58</i>
	public static final int CODEC_ID_VMDVIDEO = 53;
	/// <i>native declaration : line 59</i>
	public static final int CODEC_ID_MSZH = 54;
	/// <i>native declaration : line 60</i>
	public static final int CODEC_ID_ZLIB = 55;
	/// <i>native declaration : line 61</i>
	public static final int CODEC_ID_QTRLE = 56;
	/// <i>native declaration : line 62</i>
	public static final int CODEC_ID_SNOW = 57;
	/// <i>native declaration : line 63</i>
	public static final int CODEC_ID_TSCC = 58;
	/// <i>native declaration : line 64</i>
	public static final int CODEC_ID_ULTI = 59;
	/// <i>native declaration : line 65</i>
	public static final int CODEC_ID_QDRAW = 60;
	/// <i>native declaration : line 66</i>
	public static final int CODEC_ID_VIXL = 61;
	/// <i>native declaration : line 67</i>
	public static final int CODEC_ID_QPEG = 62;
	/// <i>native declaration : line 68</i>
	public static final int CODEC_ID_PNG = 63;
	/// <i>native declaration : line 69</i>
	public static final int CODEC_ID_PPM = 64;
	/// <i>native declaration : line 70</i>
	public static final int CODEC_ID_PBM = 65;
	/// <i>native declaration : line 71</i>
	public static final int CODEC_ID_PGM = 66;
	/// <i>native declaration : line 72</i>
	public static final int CODEC_ID_PGMYUV = 67;
	/// <i>native declaration : line 73</i>
	public static final int CODEC_ID_PAM = 68;
	/// <i>native declaration : line 74</i>
	public static final int CODEC_ID_FFVHUFF = 69;
	/// <i>native declaration : line 75</i>
	public static final int CODEC_ID_RV30 = 70;
	/// <i>native declaration : line 76</i>
	public static final int CODEC_ID_RV40 = 71;
	/// <i>native declaration : line 77</i>
	public static final int CODEC_ID_VC1 = 72;
	/// <i>native declaration : line 78</i>
	public static final int CODEC_ID_WMV3 = 73;
	/// <i>native declaration : line 79</i>
	public static final int CODEC_ID_LOCO = 74;
	/// <i>native declaration : line 80</i>
	public static final int CODEC_ID_WNV1 = 75;
	/// <i>native declaration : line 81</i>
	public static final int CODEC_ID_AASC = 76;
	/// <i>native declaration : line 82</i>
	public static final int CODEC_ID_INDEO2 = 77;
	/// <i>native declaration : line 83</i>
	public static final int CODEC_ID_FRAPS = 78;
	/// <i>native declaration : line 84</i>
	public static final int CODEC_ID_TRUEMOTION2 = 79;
	/// <i>native declaration : line 85</i>
	public static final int CODEC_ID_BMP = 80;
	/// <i>native declaration : line 86</i>
	public static final int CODEC_ID_CSCD = 81;
	/// <i>native declaration : line 87</i>
	public static final int CODEC_ID_MMVIDEO = 82;
	/// <i>native declaration : line 88</i>
	public static final int CODEC_ID_ZMBV = 83;
	/// <i>native declaration : line 89</i>
	public static final int CODEC_ID_AVS = 84;
	/// <i>native declaration : line 90</i>
	public static final int CODEC_ID_SMACKVIDEO = 85;
	/// <i>native declaration : line 91</i>
	public static final int CODEC_ID_NUV = 86;
	/// <i>native declaration : line 92</i>
	public static final int CODEC_ID_KMVC = 87;
	/// <i>native declaration : line 93</i>
	public static final int CODEC_ID_FLASHSV = 88;
	/// <i>native declaration : line 94</i>
	public static final int CODEC_ID_CAVS = 89;
	/// <i>native declaration : line 95</i>
	public static final int CODEC_ID_JPEG2000 = 90;
	/// <i>native declaration : line 96</i>
	public static final int CODEC_ID_VMNC = 91;
	/// <i>native declaration : line 97</i>
	public static final int CODEC_ID_VP5 = 92;
	/// <i>native declaration : line 98</i>
	public static final int CODEC_ID_VP6 = 93;
	/// <i>native declaration : line 99</i>
	public static final int CODEC_ID_VP6F = 94;
	/// <i>native declaration : line 100</i>
	public static final int CODEC_ID_TARGA = 95;
	/// <i>native declaration : line 101</i>
	public static final int CODEC_ID_DSICINVIDEO = 96;
	/// <i>native declaration : line 102</i>
	public static final int CODEC_ID_TIERTEXSEQVIDEO = 97;
	/// <i>native declaration : line 103</i>
	public static final int CODEC_ID_TIFF = 98;
	/// <i>native declaration : line 104</i>
	public static final int CODEC_ID_GIF = 99;
	/// <i>native declaration : line 105</i>
	public static final int CODEC_ID_FFH264 = 100;
	/// <i>native declaration : line 106</i>
	public static final int CODEC_ID_DXA = 101;
	/// <i>native declaration : line 107</i>
	public static final int CODEC_ID_DNXHD = 102;
	/// <i>native declaration : line 108</i>
	public static final int CODEC_ID_THP = 103;
	/// <i>native declaration : line 109</i>
	public static final int CODEC_ID_SGI = 104;
	/// <i>native declaration : line 110</i>
	public static final int CODEC_ID_C93 = 105;
	/// <i>native declaration : line 111</i>
	public static final int CODEC_ID_BETHSOFTVID = 106;
	/// <i>native declaration : line 112</i>
	public static final int CODEC_ID_PTX = 107;
	/// <i>native declaration : line 113</i>
	public static final int CODEC_ID_TXD = 108;
	/// <i>native declaration : line 114</i>
	public static final int CODEC_ID_VP6A = 109;
	/// <i>native declaration : line 115</i>
	public static final int CODEC_ID_AMV = 110;
	/// <i>native declaration : line 116</i>
	public static final int CODEC_ID_VB = 111;
	/// <i>native declaration : line 117</i>
	public static final int CODEC_ID_PCX = 112;
	/// <i>native declaration : line 118</i>
	public static final int CODEC_ID_SUNRAST = 113;
	/// <i>native declaration : line 119</i>
	public static final int CODEC_ID_INDEO4 = 114;
	/// <i>native declaration : line 120</i>
	public static final int CODEC_ID_INDEO5 = 115;
	/// <i>native declaration : line 121</i>
	public static final int CODEC_ID_MIMIC = 116;
	/// <i>native declaration : line 122</i>
	public static final int CODEC_ID_RL2 = 117;
	/// <i>native declaration : line 123</i>
	public static final int CODEC_ID_8SVX_EXP = 118;
	/// <i>native declaration : line 124</i>
	public static final int CODEC_ID_8SVX_FIB = 119;
	/// <i>native declaration : line 125</i>
	public static final int CODEC_ID_ESCAPE124 = 120;
	/// <i>native declaration : line 126</i>
	public static final int CODEC_ID_DIRAC = 121;
	/// <i>native declaration : line 127</i>
	public static final int CODEC_ID_BFI = 122;
	/// <i>native declaration : line 128</i>
	public static final int CODEC_ID_CMV = 123;
	/// <i>native declaration : line 129</i>
	public static final int CODEC_ID_MOTIONPIXELS = 124;
	/// <i>native declaration : line 130</i>
	public static final int CODEC_ID_TGV = 125;
	/// <i>native declaration : line 131</i>
	public static final int CODEC_ID_TGQ = 126;
	/// <i>native declaration : line 132</i>
	public static final int CODEC_ID_TQI = 127;
	/// <i>native declaration : line 133</i>
	public static final int CODEC_ID_AURA = 128;
	/// <i>native declaration : line 134</i>
	public static final int CODEC_ID_AURA2 = 129;
	/// <i>native declaration : line 135</i>
	public static final int CODEC_ID_V210X = 130;
	/// <i>native declaration : line 136</i>
	public static final int CODEC_ID_TMV = 131;
	/// <i>native declaration : line 137</i>
	public static final int CODEC_ID_V210 = 132;
	/// <i>native declaration : line 138</i>
	public static final int CODEC_ID_DPX = 133;
	/// <i>native declaration : line 139</i>
	public static final int CODEC_ID_MAD = 134;
	/// <i>native declaration : line 140</i>
	public static final int CODEC_ID_FRWU = 135;
	/// <i>native declaration : line 141</i>
	public static final int CODEC_ID_FLASHSV2 = 136;
	/// <i>native declaration : line 142</i>
	public static final int CODEC_ID_CDGRAPHICS = 137;
	/// <i>native declaration : line 143</i>
	public static final int CODEC_ID_R210 = 138;
	/// <i>native declaration : line 144</i>
	public static final int CODEC_ID_ANM = 139;
	/// <i>native declaration : line 145</i>
	public static final int CODEC_ID_BINKVIDEO = 140;
	/// <i>native declaration : line 146</i>
	public static final int CODEC_ID_IFF_ILBM = 141;
	/// <i>native declaration : line 147</i>
	public static final int CODEC_ID_IFF_BYTERUN1 = 142;
	/// <i>native declaration : line 148</i>
	public static final int CODEC_ID_KGV1 = 143;
	/// <i>native declaration : line 149</i>
	public static final int CODEC_ID_YOP = 144;
	/// <i>native declaration : line 150</i>
	public static final int CODEC_ID_VP8 = 145;
	/// <i>native declaration : line 151</i>
	public static final int CODEC_ID_PICTOR = 146;
	/// <i>native declaration : line 152</i>
	public static final int CODEC_ID_ANSI = 147;
	/// <i>native declaration : line 153</i>
	public static final int CODEC_ID_A64_MULTI = 148;
	/// <i>native declaration : line 154</i>
	public static final int CODEC_ID_A64_MULTI5 = 149;
	/// <i>native declaration : line 155</i>
	public static final int CODEC_ID_R10K = 150;
	/// <i>native declaration : line 156</i>
	public static final int CODEC_ID_MXPEG = 151;
	/// <i>native declaration : line 157</i>
	public static final int CODEC_ID_LAGARITH = 152;
	/// <i>native declaration : line 158</i>
	public static final int CODEC_ID_PRORES = 153;
	/// <i>native declaration : line 159</i>
	public static final int CODEC_ID_JV = 154;
	/// <i>native declaration : line 160</i>
	public static final int CODEC_ID_DFA = 155;
	/// <i>native declaration : line 161</i>
	public static final int CODEC_ID_WMV3IMAGE = 156;
	/// <i>native declaration : line 162</i>
	public static final int CODEC_ID_VC1IMAGE = 157;
	/// <i>native declaration : line 163</i>
	public static final int CODEC_ID_8SVX_RAW = 158;
	/// <i>native declaration : line 164</i>
	public static final int CODEC_ID_G2M = 159;
	/**
	 * various PCM "codecs"<br>
	 * various PCM "codecs"<br>
	 * <i>native declaration : line 167</i>
	 */
	public static final int CODEC_ID_FIRST_AUDIO = 65536;
	/**
	 * < A dummy id pointing at the start of audio codecs<br>
	 * <i>native declaration : line 168</i>
	 */
	public static final int CODEC_ID_PCM_S16LE = 65536;
	/// <i>native declaration : line 169</i>
	public static final int CODEC_ID_PCM_S16BE = 65537;
	/// <i>native declaration : line 170</i>
	public static final int CODEC_ID_PCM_U16LE = 65538;
	/// <i>native declaration : line 171</i>
	public static final int CODEC_ID_PCM_U16BE = 65539;
	/// <i>native declaration : line 172</i>
	public static final int CODEC_ID_PCM_S8 = 65540;
	/// <i>native declaration : line 173</i>
	public static final int CODEC_ID_PCM_U8 = 65541;
	/// <i>native declaration : line 174</i>
	public static final int CODEC_ID_PCM_MULAW = 65542;
	/// <i>native declaration : line 175</i>
	public static final int CODEC_ID_PCM_ALAW = 65543;
	/// <i>native declaration : line 176</i>
	public static final int CODEC_ID_PCM_S32LE = 65544;
	/// <i>native declaration : line 177</i>
	public static final int CODEC_ID_PCM_S32BE = 65545;
	/// <i>native declaration : line 178</i>
	public static final int CODEC_ID_PCM_U32LE = 65546;
	/// <i>native declaration : line 179</i>
	public static final int CODEC_ID_PCM_U32BE = 65547;
	/// <i>native declaration : line 180</i>
	public static final int CODEC_ID_PCM_S24LE = 65548;
	/// <i>native declaration : line 181</i>
	public static final int CODEC_ID_PCM_S24BE = 65549;
	/// <i>native declaration : line 182</i>
	public static final int CODEC_ID_PCM_U24LE = 65550;
	/// <i>native declaration : line 183</i>
	public static final int CODEC_ID_PCM_U24BE = 65551;
	/// <i>native declaration : line 184</i>
	public static final int CODEC_ID_PCM_S24DAUD = 65552;
	/// <i>native declaration : line 185</i>
	public static final int CODEC_ID_PCM_ZORK = 65553;
	/// <i>native declaration : line 186</i>
	public static final int CODEC_ID_PCM_S16LE_PLANAR = 65554;
	/// <i>native declaration : line 187</i>
	public static final int CODEC_ID_PCM_DVD = 65555;
	/// <i>native declaration : line 188</i>
	public static final int CODEC_ID_PCM_F32BE = 65556;
	/// <i>native declaration : line 189</i>
	public static final int CODEC_ID_PCM_F32LE = 65557;
	/// <i>native declaration : line 190</i>
	public static final int CODEC_ID_PCM_F64BE = 65558;
	/// <i>native declaration : line 191</i>
	public static final int CODEC_ID_PCM_F64LE = 65559;
	/// <i>native declaration : line 192</i>
	public static final int CODEC_ID_PCM_BLURAY = 65560;
	/// <i>native declaration : line 193</i>
	public static final int CODEC_ID_PCM_LXF = 65561;
	/// <i>native declaration : line 194</i>
	public static final int CODEC_ID_S302M = 65562;
	/**
	 * various ADPCM codecs<br>
	 * various ADPCM codecs<br>
	 * <i>native declaration : line 197</i>
	 */
	public static final int CODEC_ID_ADPCM_IMA_QT = 69632;
	/// <i>native declaration : line 198</i>
	public static final int CODEC_ID_ADPCM_IMA_WAV = 69633;
	/// <i>native declaration : line 199</i>
	public static final int CODEC_ID_ADPCM_IMA_DK3 = 69634;
	/// <i>native declaration : line 200</i>
	public static final int CODEC_ID_ADPCM_IMA_DK4 = 69635;
	/// <i>native declaration : line 201</i>
	public static final int CODEC_ID_ADPCM_IMA_WS = 69636;
	/// <i>native declaration : line 202</i>
	public static final int CODEC_ID_ADPCM_IMA_SMJPEG = 69637;
	/// <i>native declaration : line 203</i>
	public static final int CODEC_ID_ADPCM_MS = 69638;
	/// <i>native declaration : line 204</i>
	public static final int CODEC_ID_ADPCM_4XM = 69639;
	/// <i>native declaration : line 205</i>
	public static final int CODEC_ID_ADPCM_XA = 69640;
	/// <i>native declaration : line 206</i>
	public static final int CODEC_ID_ADPCM_ADX = 69641;
	/// <i>native declaration : line 207</i>
	public static final int CODEC_ID_ADPCM_EA = 69642;
	/// <i>native declaration : line 208</i>
	public static final int CODEC_ID_ADPCM_G726 = 69643;
	/// <i>native declaration : line 209</i>
	public static final int CODEC_ID_ADPCM_CT = 69644;
	/// <i>native declaration : line 210</i>
	public static final int CODEC_ID_ADPCM_SWF = 69645;
	/// <i>native declaration : line 211</i>
	public static final int CODEC_ID_ADPCM_YAMAHA = 69646;
	/// <i>native declaration : line 212</i>
	public static final int CODEC_ID_ADPCM_SBPRO_4 = 69647;
	/// <i>native declaration : line 213</i>
	public static final int CODEC_ID_ADPCM_SBPRO_3 = 69648;
	/// <i>native declaration : line 214</i>
	public static final int CODEC_ID_ADPCM_SBPRO_2 = 69649;
	/// <i>native declaration : line 215</i>
	public static final int CODEC_ID_ADPCM_THP = 69650;
	/// <i>native declaration : line 216</i>
	public static final int CODEC_ID_ADPCM_IMA_AMV = 69651;
	/// <i>native declaration : line 217</i>
	public static final int CODEC_ID_ADPCM_EA_R1 = 69652;
	/// <i>native declaration : line 218</i>
	public static final int CODEC_ID_ADPCM_EA_R3 = 69653;
	/// <i>native declaration : line 219</i>
	public static final int CODEC_ID_ADPCM_EA_R2 = 69654;
	/// <i>native declaration : line 220</i>
	public static final int CODEC_ID_ADPCM_IMA_EA_SEAD = 69655;
	/// <i>native declaration : line 221</i>
	public static final int CODEC_ID_ADPCM_IMA_EA_EACS = 69656;
	/// <i>native declaration : line 222</i>
	public static final int CODEC_ID_ADPCM_EA_XAS = 69657;
	/// <i>native declaration : line 223</i>
	public static final int CODEC_ID_ADPCM_EA_MAXIS_XA = 69658;
	/// <i>native declaration : line 224</i>
	public static final int CODEC_ID_ADPCM_IMA_ISS = 69659;
	/// <i>native declaration : line 225</i>
	public static final int CODEC_ID_ADPCM_G722 = 69660;
	/**
	 * AMR<br>
	 * AMR<br>
	 * <i>native declaration : line 228</i>
	 */
	public static final int CODEC_ID_AMR_NB = 73728;
	/// <i>native declaration : line 229</i>
	public static final int CODEC_ID_AMR_WB = 73729;
	/**
	 * RealAudio codecs<br>
	 * RealAudio codecs<br>
	 * <i>native declaration : line 232</i>
	 */
	public static final int CODEC_ID_RA_144 = 77824;
	/// <i>native declaration : line 233</i>
	public static final int CODEC_ID_RA_288 = 77825;
	/**
	 * various DPCM codecs<br>
	 * various DPCM codecs<br>
	 * <i>native declaration : line 236</i>
	 */
	public static final int CODEC_ID_ROQ_DPCM = 81920;
	/// <i>native declaration : line 237</i>
	public static final int CODEC_ID_INTERPLAY_DPCM = 81921;
	/// <i>native declaration : line 238</i>
	public static final int CODEC_ID_XAN_DPCM = 81922;
	/// <i>native declaration : line 239</i>
	public static final int CODEC_ID_SOL_DPCM = 81923;
	/**
	 * audio codecs<br>
	 * audio codecs<br>
	 * <i>native declaration : line 242</i>
	 */
	public static final int CODEC_ID_MP2 = 86016;
	/// <i>native declaration : line 243</i>
	public static final int CODEC_ID_MP3 = 86017;
	/**
	 * < preferred ID for decoding MPEG audio layer 1, 2 or 3<br>
	 * <i>native declaration : line 244</i>
	 */
	public static final int CODEC_ID_AAC = 86018;
	/// <i>native declaration : line 245</i>
	public static final int CODEC_ID_AC3 = 86019;
	/// <i>native declaration : line 246</i>
	public static final int CODEC_ID_DTS = 86020;
	/// <i>native declaration : line 247</i>
	public static final int CODEC_ID_VORBIS = 86021;
	/// <i>native declaration : line 248</i>
	public static final int CODEC_ID_DVAUDIO = 86022;
	/// <i>native declaration : line 249</i>
	public static final int CODEC_ID_WMAV1 = 86023;
	/// <i>native declaration : line 250</i>
	public static final int CODEC_ID_WMAV2 = 86024;
	/// <i>native declaration : line 251</i>
	public static final int CODEC_ID_MACE3 = 86025;
	/// <i>native declaration : line 252</i>
	public static final int CODEC_ID_MACE6 = 86026;
	/// <i>native declaration : line 253</i>
	public static final int CODEC_ID_VMDAUDIO = 86027;
	/// <i>native declaration : line 254</i>
	public static final int CODEC_ID_SONIC = 86028;
	/// <i>native declaration : line 255</i>
	public static final int CODEC_ID_SONIC_LS = 86029;
	/// <i>native declaration : line 256</i>
	public static final int CODEC_ID_FLAC = 86030;
	/// <i>native declaration : line 257</i>
	public static final int CODEC_ID_MP3ADU = 86031;
	/// <i>native declaration : line 258</i>
	public static final int CODEC_ID_MP3ON4 = 86032;
	/// <i>native declaration : line 259</i>
	public static final int CODEC_ID_SHORTEN = 86033;
	/// <i>native declaration : line 260</i>
	public static final int CODEC_ID_ALAC = 86034;
	/// <i>native declaration : line 261</i>
	public static final int CODEC_ID_WESTWOOD_SND1 = 86035;
	/// <i>native declaration : line 262</i>
	public static final int CODEC_ID_GSM = 86036;
	/**
	 * < as in Berlin toast format<br>
	 * <i>native declaration : line 263</i>
	 */
	public static final int CODEC_ID_QDM2 = 86037;
	/// <i>native declaration : line 264</i>
	public static final int CODEC_ID_COOK = 86038;
	/// <i>native declaration : line 265</i>
	public static final int CODEC_ID_TRUESPEECH = 86039;
	/// <i>native declaration : line 266</i>
	public static final int CODEC_ID_TTA = 86040;
	/// <i>native declaration : line 267</i>
	public static final int CODEC_ID_SMACKAUDIO = 86041;
	/// <i>native declaration : line 268</i>
	public static final int CODEC_ID_QCELP = 86042;
	/// <i>native declaration : line 269</i>
	public static final int CODEC_ID_WAVPACK = 86043;
	/// <i>native declaration : line 270</i>
	public static final int CODEC_ID_DSICINAUDIO = 86044;
	/// <i>native declaration : line 271</i>
	public static final int CODEC_ID_IMC = 86045;
	/// <i>native declaration : line 272</i>
	public static final int CODEC_ID_MUSEPACK7 = 86046;
	/// <i>native declaration : line 273</i>
	public static final int CODEC_ID_MLP = 86047;
	/// <i>native declaration : line 274</i>
	public static final int CODEC_ID_GSM_MS = 86048;
	/// <i>native declaration : line 275</i>
	public static final int CODEC_ID_ATRAC3 = 86049;
	/// <i>native declaration : line 276</i>
	public static final int CODEC_ID_VOXWARE = 86050;
	/// <i>native declaration : line 277</i>
	public static final int CODEC_ID_APE = 86051;
	/// <i>native declaration : line 278</i>
	public static final int CODEC_ID_NELLYMOSER = 86052;
	/// <i>native declaration : line 279</i>
	public static final int CODEC_ID_MUSEPACK8 = 86053;
	/// <i>native declaration : line 280</i>
	public static final int CODEC_ID_SPEEX = 86054;
	/// <i>native declaration : line 281</i>
	public static final int CODEC_ID_WMAVOICE = 86055;
	/// <i>native declaration : line 282</i>
	public static final int CODEC_ID_WMAPRO = 86056;
	/// <i>native declaration : line 283</i>
	public static final int CODEC_ID_WMALOSSLESS = 86057;
	/// <i>native declaration : line 284</i>
	public static final int CODEC_ID_ATRAC3P = 86058;
	/// <i>native declaration : line 285</i>
	public static final int CODEC_ID_EAC3 = 86059;
	/// <i>native declaration : line 286</i>
	public static final int CODEC_ID_SIPR = 86060;
	/// <i>native declaration : line 287</i>
	public static final int CODEC_ID_MP1 = 86061;
	/// <i>native declaration : line 288</i>
	public static final int CODEC_ID_TWINVQ = 86062;
	/// <i>native declaration : line 289</i>
	public static final int CODEC_ID_TRUEHD = 86063;
	/// <i>native declaration : line 290</i>
	public static final int CODEC_ID_MP4ALS = 86064;
	/// <i>native declaration : line 291</i>
	public static final int CODEC_ID_ATRAC1 = 86065;
	/// <i>native declaration : line 292</i>
	public static final int CODEC_ID_BINKAUDIO_RDFT = 86066;
	/// <i>native declaration : line 293</i>
	public static final int CODEC_ID_BINKAUDIO_DCT = 86067;
	/// <i>native declaration : line 294</i>
	public static final int CODEC_ID_AAC_LATM = 86068;
	/// <i>native declaration : line 295</i>
	public static final int CODEC_ID_QDMC = 86069;
	/// <i>native declaration : line 296</i>
	public static final int CODEC_ID_CELT = 86070;
	/**
	 * subtitle codecs<br>
	 * subtitle codecs<br>
	 * <i>native declaration : line 299</i>
	 */
	public static final int CODEC_ID_FIRST_SUBTITLE = 94208;
	/**
	 * < A dummy ID pointing at the start of subtitle codecs.<br>
	 * <i>native declaration : line 300</i>
	 */
	public static final int CODEC_ID_DVD_SUBTITLE = 94208;
	/// <i>native declaration : line 301</i>
	public static final int CODEC_ID_DVB_SUBTITLE = 94209;
	/// <i>native declaration : line 302</i>
	public static final int CODEC_ID_TEXT = 94210;
	/**
	 * < raw UTF-8 text<br>
	 * <i>native declaration : line 303</i>
	 */
	public static final int CODEC_ID_XSUB = 94211;
	/// <i>native declaration : line 304</i>
	public static final int CODEC_ID_SSA = 94212;
	/// <i>native declaration : line 305</i>
	public static final int CODEC_ID_MOV_TEXT = 94213;
	/// <i>native declaration : line 306</i>
	public static final int CODEC_ID_HDMV_PGS_SUBTITLE = 94214;
	/// <i>native declaration : line 307</i>
	public static final int CODEC_ID_DVB_TELETEXT = 94215;
	/// <i>native declaration : line 308</i>
	public static final int CODEC_ID_SRT = 94216;
	/// <i>native declaration : line 309</i>
	public static final int CODEC_ID_MICRODVD = 94217;
	/**
	 * other specific kind of codecs (generally used for attachments)<br>
	 * other specific kind of codecs (generally used for attachments)<br>
	 * <i>native declaration : line 312</i>
	 */
	public static final int CODEC_ID_FIRST_UNKNOWN = 98304;
	/**
	 * < A dummy ID pointing at the start of various fake codecs.<br>
	 * <i>native declaration : line 313</i>
	 */
	public static final int CODEC_ID_TTF = 98304;
	/// <i>native declaration : line 314</i>
	public static final int CODEC_ID_BINTEXT = 98305;
	/// <i>native declaration : line 315</i>
	public static final int CODEC_ID_XBIN = 98306;
	/// <i>native declaration : line 316</i>
	public static final int CODEC_ID_IDF = 98307;
	/// <i>native declaration : line 318</i>
	public static final int CODEC_ID_PROBE = 102400;
	/// <i>native declaration : line 320</i>
	public static final int CODEC_ID_MPEG2TS = 131072;
	/// <i>native declaration : line 322</i>
	public static final int CODEC_ID_FFMETADATA = 135168;
};

