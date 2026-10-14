package com.edijs.training;
/** Compression choices independent of Android codecs, so they can be checked on the JVM. */
public final class CompressionPolicy {
 public static final int VIDEO_BITRATE=4000000;
 public static int[] dimensions(int width,int height,int rotation){if(width<=0||height<=0)throw new IllegalArgumentException("Missing dimensions");if(rotation==90||rotation==270){int swap=width;width=height;height=swap;}double scale=Math.min(1d,Math.min(1920d/Math.max(width,height),1080d/Math.min(width,height)));return new int[]{Math.max(2,((int)(width*scale)/2)*2),Math.max(2,((int)(height*scale)/2)*2)};}
 public static boolean useCompressed(long originalBytes,long outputBytes,boolean originalCompatible){if(outputBytes<=0)throw new IllegalArgumentException("Empty export");return !originalCompatible||originalBytes<=0||outputBytes<originalBytes;}
}
