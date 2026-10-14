package com.edijs.training;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.media.MediaMetadataRetriever;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VideoCompressor {
 public interface Result {void ready(Uri[] uris);}
 private final Activity activity;private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService metadataWorker=Executors.newSingleThreadExecutor();
 private Transformer transformer;private AlertDialog dialog;private TextView label;private ProgressBar progress;private File output;private boolean cancelled=false,closed=false;private Result callback;
 private ArrayList<Uri> inputs,results;private int index;private Uri current;private long originalBytes;private boolean compatible;
 public VideoCompressor(Activity activity){this.activity=activity;File folder=new File(activity.getCacheDir(),"compressed-videos");folder.mkdirs();File[] files=folder.listFiles();if(files!=null)for(File f:files)if(f.lastModified()<System.currentTimeMillis()-24*60*60*1000L)f.delete();}
 public void prepare(ArrayList<Uri> uris,Result callback){if(closed)return;this.callback=callback;inputs=uris;results=new ArrayList<>();index=0;cancelled=false;next();}
 private void next(){if(cancelled||closed)return;if(index>=inputs.size()){finish(results.toArray(new Uri[0]));return;}current=inputs.get(index);String mime=activity.getContentResolver().getType(current);if(mime==null||!mime.startsWith("video/")){results.add(current);index++;next();return;}
  LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(40,32,40,24);label=new TextView(activity);label.setText("Saspiež video / Compressing video…");layout.addView(label);progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setIndeterminate(true);layout.addView(progress);
  dialog=new AlertDialog.Builder(activity).setTitle("PIIPIBUBUS LAB").setView(layout).setCancelable(false).setNegativeButton("Atcelt / Cancel",(d,w)->cancel()).create();dialog.show();activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  Uri source=current;metadataWorker.execute(()->{try{
   int width,height,rotation;MediaMetadataRetriever metadata=new MediaMetadataRetriever();try{metadata.setDataSource(activity,source);width=Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));height=Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));String r=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);rotation=r==null?0:Integer.parseInt(r);}finally{metadata.release();}
   long bytes=0;try(Cursor c=activity.getContentResolver().query(source,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))bytes=c.getLong(0);}
   boolean h264=false;MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(activity,source,null);for(int i=0;i<extractor.getTrackCount();i++){MediaFormat format=extractor.getTrackFormat(i);if(MimeTypes.VIDEO_H264.equals(format.getString(MediaFormat.KEY_MIME)))h264=true;}}finally{extractor.release();}
   int[] size=CompressionPolicy.dimensions(width,height,rotation);long original=bytes;boolean alreadyCompatible=h264&&"video/mp4".equals(activity.getContentResolver().getType(source));ui.post(()->{if(!cancelled&&!closed){originalBytes=original;compatible=alreadyCompatible;start(size[0],size[1]);}});
  }catch(Exception e){ui.post(this::failed);}});
 }
 private void start(int width,int height){try{
  output=new File(new File(activity.getCacheDir(),"compressed-videos"),"piipibubus-"+UUID.randomUUID()+".mp4");
  EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(current)).setEffects(new Effects(Collections.emptyList(),Collections.singletonList(Presentation.createForWidthAndHeight(width,height,Presentation.LAYOUT_SCALE_TO_FIT)))).build();
  EditedMediaItemSequence sequence=EditedMediaItemSequence.withAudioAndVideoFrom(Collections.singletonList(item));
  Composition composition=new Composition.Builder(sequence).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build();
  DefaultEncoderFactory encoders=new DefaultEncoderFactory.Builder(activity).setEnableFallback(true).setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(CompressionPolicy.VIDEO_BITRATE).build()).build();
  transformer=new Transformer.Builder(activity).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoders).addListener(new Transformer.Listener(){
   @Override public void onCompleted(Composition c,ExportResult result){if(cancelled||closed)return;try{boolean useOutput=CompressionPolicy.useCompressed(originalBytes,output.length(),compatible);Uri chosen=useOutput?FileProvider.getUriForFile(activity,activity.getPackageName()+".files",output):current;if(!useOutput)output.delete();results.add(chosen);output=null;dismiss();transformer=null;index++;next();}catch(Exception e){failed();}}
   @Override public void onError(Composition c,ExportResult result,ExportException error){if(!cancelled&&!closed)failed();}
  }).build();transformer.start(composition,output.getAbsolutePath());updateProgress();
 }catch(Exception e){failed();}}
 private void updateProgress(){if(transformer==null||cancelled||closed)return;ProgressHolder holder=new ProgressHolder();int state=transformer.getProgress(holder);progress.setIndeterminate(state!=Transformer.PROGRESS_STATE_AVAILABLE);if(state==Transformer.PROGRESS_STATE_AVAILABLE){progress.setProgress(holder.progress);label.setText("Saspiež video / Compressing video: "+holder.progress+"%");}ui.postDelayed(this::updateProgress,400);}
 private void failed(){if(cancelled||closed)return;if(transformer!=null){transformer.cancel();transformer=null;}if(output!=null){output.delete();output=null;}dismiss();new AlertDialog.Builder(activity).setTitle("Video saspiešana neizdevās / Compression failed").setMessage("Šis telefons nevar saspiest izvēlēto video. Oriģināls nav mainīts. Vari nosūtīt oriģinālu vai izvēlēties citu video. / This phone cannot compress this video. The original is unchanged. Send the original or choose another video.").setPositiveButton("Oriģināls / Original",(d,w)->{results.add(current);index++;next();}).setNegativeButton("Atcelt / Cancel",(d,w)->cancel()).setOnCancelListener(d->cancel()).show();}
 private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
 private void finish(Uri[] values){dismiss();Result done=callback;callback=null;if(done!=null&&!closed)done.ready(values);}
 public void cancel(){cancelled=true;if(transformer!=null){transformer.cancel();transformer=null;}if(output!=null){output.delete();output=null;}finish(null);}
 public void close(){cancel();closed=true;ui.removeCallbacksAndMessages(null);metadataWorker.shutdownNow();}
}
