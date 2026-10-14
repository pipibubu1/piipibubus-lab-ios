package com.edijs.training;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private static final String ORIGIN="https://edijs-calisthenics.groovycocoa1.chatgpt.site";
 private VideoCompressor compressor;
 private WebMessagePort feedbackPort; private long lastFeedback;
 private WebView web; private ValueCallback<Uri[]> fileCallback;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private String loginId,verifier,appUserAgent; private long loginUntil; private boolean starting=false,checking=false;
 private TextView status; private LinearLayout loginBar; private boolean destroyed=false;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().setStatusBarColor(0xff141713);getWindow().setNavigationBarColor(0xff141713);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff141713);
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  loginBar=new LinearLayout(this);loginBar.setOrientation(LinearLayout.VERTICAL);loginBar.setPadding(24,12,24,12);loginBar.setVisibility(View.GONE);
  status=new TextView(this);status.setTextColor(0xffd8ee81);loginBar.addView(status);
  Button cancel=new Button(this);cancel.setText("Atcelt / Cancel");cancel.setOnClickListener(v->cancelLogin());loginBar.addView(cancel);root.addView(loginBar);
  compressor=new VideoCompressor(this);web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setUserAgentString(settings.getUserAgentString()+" PIIPIBUBUSLAB/4");appUserAgent=settings.getUserAgentString();
  if(android.os.Build.VERSION.SDK_INT>=26)settings.setSafeBrowsingEnabled(true);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return request.isForMainFrame()&&navigate(request.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return navigate(Uri.parse(url));}
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){closeFeedback();}
   @Override public void onPageFinished(WebView view,String url){CookieManager.getInstance().flush();connectFeedback(url);}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())Toast.makeText(MainActivity.this,"Pārbaudi internetu un mēģini vēlreiz. / Check your connection.",Toast.LENGTH_LONG).show();}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
    String[] types=params.getAcceptTypes();if(types!=null&&types.length>0){java.util.ArrayList<String> mimes=new java.util.ArrayList<>();boolean onlyVideo=true;for(String type:types){if(type!=null&&type.contains("/")){mimes.add(type);if(!type.startsWith("video/"))onlyVideo=false;}}if(!mimes.isEmpty()){intent.putExtra(Intent.EXTRA_MIME_TYPES,mimes.toArray(new String[0]));if(onlyVideo)intent.setType("video/*");}}
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
    try{startActivityForResult(intent,50);}catch(ActivityNotFoundException e){fileCallback.onReceiveValue(null);fileCallback=null;}return true;
   }
  });
  web.setDownloadListener((url,agent,disposition,mime,size)->{
   Uri uri=Uri.parse(url);if(!sameSite(uri)){external(uri);return;}
   try{DownloadManager.Request request=new DownloadManager.Request(uri);String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null)request.addRequestHeader("Cookie",cookies);request.setMimeType(mime);request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);request.setTitle("PIIPIBUBUS LAB");((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);Toast.makeText(this,"Lejupielāde sākta / Download started",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Neizdevās lejupielādēt / Download failed",Toast.LENGTH_LONG).show();}
  });
  web.loadUrl(ORIGIN+"/account");
 }
 private void closeFeedback(){if(feedbackPort!=null){feedbackPort.close();feedbackPort=null;}}
 private void connectFeedback(String url){
  closeFeedback();if(destroyed||!sameSite(Uri.parse(url))||!sameSite(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;
  WebMessagePort[] ports=web.createWebMessageChannel();feedbackPort=ports[0];
  feedbackPort.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){
   @Override public void onMessage(WebMessagePort channel,WebMessage message){
    if(channel!=feedbackPort||destroyed||!web.hasWindowFocus()||!sameSite(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;
    String body=message.getData();if(body==null||body.length()>64)return;
    try{JSONObject payload=new JSONObject(body);String type=payload.optString("type","");if(payload.length()!=1||!("tap".equals(type)||"success".equals(type)))return;
     long instant=android.os.SystemClock.elapsedRealtime();if(instant-lastFeedback<150)return;lastFeedback=instant;
     web.performHapticFeedback("tap".equals(type)?HapticFeedbackConstants.KEYBOARD_TAP:HapticFeedbackConstants.LONG_PRESS);
    }catch(Exception ignored){}
   }
  },ui);
  web.postWebMessage(new WebMessage("__labFeedbackPort",new WebMessagePort[]{ports[1]}),Uri.parse(ORIGIN));
 }
 private boolean sameSite(Uri uri){return "https".equals(uri.getScheme())&&"edijs-calisthenics.groovycocoa1.chatgpt.site".equals(uri.getHost())&&(uri.getPort()==-1||uri.getPort()==443);}
 private boolean navigate(Uri uri){if(sameSite(uri)){if("/auth/google".equals(uri.getPath())){startLogin();return true;}return false;}external(uri);return true;}
 private void external(Uri uri){String scheme=uri.getScheme();if(!"https".equals(scheme)&&!"mailto".equals(scheme)&&!"tel".equals(scheme))return;try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setMessage("Google pieslēgšanai vajadzīgs pārlūks, piemēram, Firefox. / Install a browser such as Firefox for Google sign-in.").setPositiveButton("OK",null).show();}}
 private static String hex(byte[] data){StringBuilder value=new StringBuilder();for(byte b:data)value.append(String.format("%02x",b&255));return value.toString();}
 private void startLogin(){if(starting||loginId!=null)return;starting=true;status.setText("Sāk pieslēgšanos / Starting sign-in…");loginBar.setVisibility(View.VISIBLE);
  worker.execute(()->{try{
   SecureRandom random=new SecureRandom();byte[] token=new byte[32];random.nextBytes(token);String proof=hex(token),challenge=hex(MessageDigest.getInstance("SHA-256").digest(proof.getBytes(StandardCharsets.UTF_8))),pairing=String.format(java.util.Locale.ROOT,"%06d",random.nextInt(1000000));
   JSONObject response=post("start","challenge="+challenge+"&pairing="+pairing);String id=response.getString("id");
   ui.post(()->{if(destroyed||!starting)return;starting=false;verifier=proof;loginId=id;loginUntil=System.currentTimeMillis()+300000;status.setText("Pieslēgšanās kods / Sign-in code: "+pairing+"\nPēc apstiprināšanas atgriezies lietotnē. / Return to the app after confirmation.");
    String next="/auth/native/complete?id="+id;external(Uri.parse(ORIGIN+"/auth/google?next="+Uri.encode(next)));ui.postDelayed(this::checkLogin,2000);
   });
  }catch(Exception e){ui.post(()->{starting=false;cancelLogin();Toast.makeText(this,"Pieslēgšanās neizdevās. Mēģini vēlreiz. / Sign-in failed. Try again.",Toast.LENGTH_LONG).show();});}});
 }
 private JSONObject post(String action,String body)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL(ORIGIN+"/auth/native/"+action).openConnection();connection.setRequestProperty("User-Agent",appUserAgent);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
  try{try(OutputStream stream=connection.getOutputStream()){stream.write(body.getBytes(StandardCharsets.UTF_8));}int code=connection.getResponseCode();if(code!=200)throw new IOException("Sign-in request failed");try(InputStream stream=connection.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1){result.write(buffer,0,n);if(result.size()>16384)throw new IOException("Response too large");}return new JSONObject(result.toString("UTF-8"));}}finally{connection.disconnect();}
 }
 private void checkLogin(){if(destroyed||checking||loginId==null)return;if(System.currentTimeMillis()>loginUntil){cancelLogin();Toast.makeText(this,"Pieslēgšanās laiks beidzies / Sign-in expired",Toast.LENGTH_LONG).show();return;}
  checking=true;String id=loginId,proof=verifier;worker.execute(()->{boolean ready=false;try{ready=post("status","id="+id+"&verifier="+proof).optBoolean("ready");}catch(Exception ignored){}boolean approved=ready;ui.post(()->{checking=false;if(destroyed||!id.equals(loginId))return;if(approved){web.postUrl(ORIGIN+"/auth/native/exchange",("id="+id+"&verifier="+proof).getBytes(StandardCharsets.UTF_8));cancelLogin();}else ui.postDelayed(this::checkLogin,2500);});});
 }
 private void cancelLogin(){loginId=null;verifier=null;starting=false;loginBar.setVisibility(View.GONE);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);checkLogin();}
 @Override public void onResume(){super.onResume();if(web!=null)web.onResume();checkLogin();}
 @Override public void onPause(){super.onPause();if(web!=null){web.onPause();CookieManager.getInstance().flush();}}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==50&&fileCallback!=null){Uri[] values=null;if(result==RESULT_OK&&data!=null){java.util.ArrayList<Uri> selected=new java.util.ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri uri=data.getClipData().getItemAt(i).getUri();if("content".equals(uri.getScheme()))selected.add(uri);}}else if(data.getData()!=null&&"content".equals(data.getData().getScheme()))selected.add(data.getData());if(!selected.isEmpty())values=selected.toArray(new Uri[0]);}ValueCallback<Uri[]> pending=fileCallback;fileCallback=null;if(values==null)pending.onReceiveValue(null);else compressor.prepare(new java.util.ArrayList<>(java.util.Arrays.asList(values)),pending::onReceiveValue);}}
 @Override public void onDestroy(){closeFeedback();destroyed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();if(compressor!=null)compressor.close();if(fileCallback!=null)fileCallback.onReceiveValue(null);web.destroy();super.onDestroy();}
}
