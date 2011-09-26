/*
 *  ffmpeg_mr.cp
 *  ffmpeg-mr
 *
 *  Created by Tom Stordy-Allison on 26/09/2011.
 *  Copyright (c) 2011 __MyCompanyName__. All rights reserved.
 *
 */

#include <iostream>
#include "ffmpeg_mr.h"
#include "ffmpeg_mrPriv.h"

void ffmpeg_mr::HelloWorld(const char * s)
{
	 ffmpeg_mrPriv *theObj = new ffmpeg_mrPriv;
	 theObj->HelloWorldPriv(s);
	 delete theObj;
};

void ffmpeg_mrPriv::HelloWorldPriv(const char * s) 
{
	std::cout << s << std::endl;
};

