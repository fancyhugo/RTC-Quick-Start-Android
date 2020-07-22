package cn.rongcloud.quickstart;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import cn.rongcloud.rtc.api.RCRTCConfig.Builder;
import cn.rongcloud.rtc.api.RCRTCEngine;
import cn.rongcloud.rtc.api.RCRTCRemoteUser;
import cn.rongcloud.rtc.api.RCRTCRoom;
import cn.rongcloud.rtc.api.callback.IRCRTCAudioDataListener;
import cn.rongcloud.rtc.api.callback.IRCRTCResultCallback;
import cn.rongcloud.rtc.api.callback.IRCRTCResultDataCallback;
import cn.rongcloud.rtc.api.callback.IRCRTCRoomEventsListener;
import cn.rongcloud.rtc.api.callback.IRCRTCStatusReportListener;
import cn.rongcloud.rtc.api.callback.IRCRTCVideoOutputFrameListener;
import cn.rongcloud.rtc.api.callback.IRCRTCVideoSource;
import cn.rongcloud.rtc.api.callback.IRCRTCVideoSource.IRCVideoConsumer;
import cn.rongcloud.rtc.api.report.StatusReport;
import cn.rongcloud.rtc.api.stream.RCRTCAudioStreamConfig;
import cn.rongcloud.rtc.api.stream.RCRTCInputStream;
import cn.rongcloud.rtc.api.stream.RCRTCOutputStream;
import cn.rongcloud.rtc.api.stream.RCRTCVideoInputStream;
import cn.rongcloud.rtc.api.stream.RCRTCVideoOutputStream;
import cn.rongcloud.rtc.api.stream.RCRTCVideoStreamConfig;
import cn.rongcloud.rtc.api.stream.RCRTCVideoView;
import cn.rongcloud.rtc.base.RCRTCAudioFrame;
import cn.rongcloud.rtc.base.RCRTCMediaType;
import cn.rongcloud.rtc.base.RCRTCParamsType.RCRTCVideoFps;
import cn.rongcloud.rtc.base.RCRTCParamsType.RCRTCVideoResolution;
import cn.rongcloud.rtc.base.RCRTCVideoFrame;
import cn.rongcloud.rtc.base.RTCErrorCode;
import io.rong.common.utils.SSLUtils;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.RongIMClient.ConnectCallback;
import io.rong.imlib.RongIMClient.ConnectionErrorCode;
import io.rong.imlib.RongIMClient.DatabaseOpenStatus;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.rtcdemo.R;
import javax.net.ssl.SSLContext;


public class TestActivity extends Activity {


  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    //配置 https 签名证书校验实现，注意，需要在融云IM init 方法之前调用
    //因为包含证书忽略的代码，上架google应用商店被驳回，私有云证书校验的代码需要由 app 层进行设置
    configHttpsCertificate();
    test();
  }

  private void  configHttpsCertificate(){
    SSLUtils.setSSLContext(AppSSLUtils.getSSLContext());
    SSLUtils.setHostnameVerifier(AppSSLUtils.DO_NOT_VERIFY);
  }


  private void test() {
    RongIMClient.init(getApplication());
    RongIMClient.connect("token", 10, new ConnectCallback() {
      @Override
      public void onSuccess(String userId) {
        init();
        joinRoom();
      }

      @Override
      public void onError(ConnectionErrorCode connectionErrorCode) {

      }

      @Override
      public void onDatabaseOpened(DatabaseOpenStatus databaseOpenStatus) {

      }
    });
  }

  private void init(){
    // config 如果没有特殊的需求，不需要配置，直接使用SDK默认实现即可
    Builder configBuilder = Builder.create();
    //设置硬件编码解码，默认true
    configBuilder.enableHardwareDecoder(true);
    configBuilder.enableHardwareEncoder(true);
    //4.0.1版本这个配置已去掉
    //configBuilder.enableHttpsSelfCertificate(true);
    //默认true,如果设置为false，网络异常rtcPing 连续4次失败时会主动退出房间，callLib 会设置false
    configBuilder.enableAutoReconnect(true);
    // iOS 对 Android 发送的 highProfile 支持不太好，目前SDK已把这个值设置为false
    configBuilder.enableHardwareEncoderHighProfile(false);
    //默认是true
    configBuilder.enableEncoderTexture(true);
    //
    RCRTCEngine.getInstance().init(getApplicationContext(), configBuilder.build());
  }

  private RCRTCRoom room;

  private void joinRoom() {
    RCRTCEngine.getInstance().joinRoom("roomId", new IRCRTCResultDataCallback<RCRTCRoom>() {
      @Override
      public void onSuccess(RCRTCRoom rcrtcRoom) {
        room = rcrtcRoom;
        RCRTCVideoView rtcVideoView = new RCRTCVideoView(TestActivity.this);
        RCRTCEngine.getInstance().getDefaultVideoStream().setVideoView(rtcVideoView);
        //打开摄像头开始预览
        RCRTCEngine.getInstance().getDefaultVideoStream().startCamera(
            new IRCRTCResultDataCallback<Boolean>() {
              @Override
              public void onSuccess(Boolean aBoolean) {

              }

              @Override
              public void onFailed(RTCErrorCode rtcErrorCode) {

              }
            });
        //发布资源
        publishResource();
        //设置加入房间后的事件监听
        addRoomEventListener();
        //加入房间时，房间内已经有用户发布过资源
        subscribeResource();
        //发布自定义音频流
        publishCustomStream();
        //发布音视频流或者订阅别人的音视频流后，音视频流的网络传输状态统计
        registerStatusReportListener();
        //本地和远端音频原始pcm数据流回调给app层，app 层可以做一些处理后返回给RTCLib
        //可以分别录制本地和远端的音频，RTCLib的录音功能目前在4.0.0+开发中，还未上线
        registerAudioBufferListener();
        //本地摄像头采集的视频流回调,美颜处理
        registerLocalVideoBufferListener();
        //设置房间属性，做一些特殊化的定制需求
        setRoomAttribute();
        //设置我自己的属性
        setLocalUserAttribute();
      }

      @Override
      public void onFailed(RTCErrorCode rtcErrorCode) {

      }
    });
  }


  private void publishResource() {
    //视频配置相关的参数，如无特殊需求，不需要专门配置，使用SDK默认配置即可
    RCRTCVideoStreamConfig.Builder videoConfigBuilder = RCRTCVideoStreamConfig.Builder.create();
    //设置分辨率
    videoConfigBuilder.setVideoResolution(RCRTCVideoResolution.RESOLUTION_480_640);
    //设置帧率
    videoConfigBuilder.setVideoFps(RCRTCVideoFps.Fps_15);
    //设置最小码率，480P下推荐200
    videoConfigBuilder.setMinRate(200);
    //设置最大码率，480P下推荐900
    videoConfigBuilder.setMaxRate(900);
    RCRTCEngine.getInstance().getDefaultVideoStream().setVideoConfig(videoConfigBuilder.build());
    //设置SDK默认 camera 视频流是否打开小流
    RCRTCEngine.getInstance().getDefaultVideoStream().enableTinyStream(true);
    //设置摄像头采集的角度
    RCRTCEngine.getInstance().getDefaultVideoStream().setCameraDisplayOrientation(0);

    //音频相关的配置参数，大部分开发者不需要特殊配置参数，使用SDK内部配置即可，所以不需要调用
    RCRTCAudioStreamConfig.Builder audioConfigBuilder = RCRTCAudioStreamConfig.Builder.create();
    //音频相关的配置参数很多非常专业，主要给硬件OEM厂商使用，大部分开发者不需要，使用SDK默认配置即可，只是功能展示
    audioConfigBuilder.enableAGCControl(true)
        .enableHighPassFilter(true);
    //有客户是做音乐类相关的线上培训，我们专门加了音乐模式，普通客户不需要，只是功能展示
    boolean isMusicMode = false;
    RCRTCAudioStreamConfig audioStreamConfig = isMusicMode ?
        audioConfigBuilder.buildMusicMode() : audioConfigBuilder.buildDefaultMode();
    RCRTCEngine.getInstance().getDefaultAudioStream().setAudioConfig(audioStreamConfig);
    //发布SDK默认支持的音视频流，默认采集麦克风声音，前置摄像头视频
    room.getLocalUser().publishDefaultStreams(new IRCRTCResultCallback() {
      @Override
      public void onSuccess() {

      }

      @Override
      public void onFailed(RTCErrorCode rtcErrorCode) {
      }
    });
    //如果想只发布SDK支持的默认音频资源，不发布视频资源
    List<RCRTCOutputStream> localAvStreams = new ArrayList<>();
    localAvStreams.add(RCRTCEngine.getInstance().getDefaultAudioStream());
    room.getLocalUser().publishStreams(localAvStreams, new IRCRTCResultCallback() {
      @Override
      public void onSuccess() {

      }

      @Override
      public void onFailed(RTCErrorCode rtcErrorCode) {

      }
    });
  }

  private void addRoomEventListener() {
    room.registerRoomListener(new IRCRTCRoomEventsListener() {
      //远端用户发布资源,streams 是新发布的资源
      @Override
      public void onRemoteUserPublishResource(RCRTCRemoteUser rcrtcRemoteUser,
          List<RCRTCInputStream> streams) {

      }

      //远端用户静音或者开启麦克风
      @Override
      public void onRemoteUserMuteAudio(RCRTCRemoteUser rcrtcRemoteUser,
          RCRTCInputStream rcrtcInputStream, boolean mute) {

      }

      //远端用户打开或者关闭摄像头
      @Override
      public void onRemoteUserMuteVideo(RCRTCRemoteUser rcrtcRemoteUser,
          RCRTCInputStream rcrtcInputStream, boolean mute) {

      }

      //远端用户取消发布资源,streams 是取消发布的资源
      @Override
      public void onRemoteUserUnpublishResource(RCRTCRemoteUser rcrtcRemoteUser,
          List<RCRTCInputStream> list) {
        //远端用户取消发布资源，如果是视频资源，app 层 移除 RongRTCVideoView 即可
        // RTCLib SDK 内部已经做了取消订阅资源的操作，不需要开发者主动在这个通知里取消订阅资源
      }

      //远端用户加入房间，指加入IM server 信令房间，此时通常情况下还没有发布资源
      @Override
      public void onUserJoined(RCRTCRemoteUser rcrtcRemoteUser) {

      }

      //远端用户主动离开入房间，SDK内部会主动取消订阅这个用户发布的资源，app层做UI更新即可
      @Override
      public void onUserLeft(RCRTCRemoteUser rcrtcRemoteUser) {

      }

      //远端用户被 IM server踢下房间，SDK内部会主动取消订阅这个用户发布的资源，app层做UI更新即可
      //用户加入房间后，默认每10s发一次 rtcPing 到 IM  server,如果因为本地断网等原因长时间没有rtcPing
      //IM server 在大概 1分钟会把这个用户踢除房间
      @Override
      public void onUserOffline(RCRTCRemoteUser rcrtcRemoteUser) {

      }

      //底层媒体引擎层 webRTC 上报的通知事件，订阅远端视频流成功后，用于传输远端视频流的 videoTrack 在本地创建成功
      //此时通过webRTC udp 通道传输的远端视频流还没有到达本地
      @Override
      public void onVideoTrackAdd(String userId, String tag) {

      }

      //渲染 RCRTCVideoView 的第一帧回调通知
      //如果是远端用户的 RCRTCVideoView ，onFirstFrameDraw的调用肯定是发生在onVideoTrackAdd之后
      @Override
      public void onFirstRemoteVideoFrame(String userId, String tag) {
      }

      //因一些异常原因，RTCLib SDK内部主动退出房间
      //对应config配置中的enableAutoReconnect
      @Override
      public void onLeaveRoom(int reasonCode) {

      }

      //接收到房间内 开发者自定义IM消息
      //注意，这个回调只能接收到当前加入房间内的消息，不会收到融云IM的私聊，群组，聊天室等的消息
      @Override
      public void onReceiveMessage(Message message) {

      }

      //目前融云server api 提供主动踢人的功能
      @Override
      public void onKickedByServer() {

      }
    });
  }

  private void subscribeResource() {
    for (final RCRTCRemoteUser remoteUser : room.getRemoteUsers()) {
      subscribeResource(remoteUser, remoteUser.getStreams());
    }
  }

  private void subscribeResource(RCRTCRemoteUser remoteUser, List<RCRTCInputStream> streams) {
    for (RCRTCInputStream inputStream : streams) {
      if (inputStream.getMediaType() == RCRTCMediaType.VIDEO) {
        RCRTCVideoView rtcVideoView = new RCRTCVideoView(TestActivity.this);
        ((RCRTCVideoInputStream) inputStream).setVideoView(rtcVideoView);
      }
    }
    room.getLocalUser().subscribeStreams(streams, new IRCRTCResultCallback() {
      @Override
      public void onSuccess() {

      }

      @Override
      public void onFailed(RTCErrorCode rtcErrorCode) {

      }
    });
  }

  IRCVideoConsumer videoConsumer;
  private void publishCustomStream() {

    //设置自定义视频的分辨率、帧率、码率信息。默认是480x640@15f
    RCRTCVideoStreamConfig.Builder videoConfigBuilder = RCRTCVideoStreamConfig.Builder.create();
    videoConfigBuilder.setVideoResolution(RCRTCVideoResolution.RESOLUTION_480_640);
    RCRTCVideoOutputStream mOutputStream = RCRTCEngine.getInstance().createVideoStream("STREAM_TAG", videoConfigBuilder.build());
    //设置自定义视频数据来源：可以如下方式实现，也可以新建一个IRCRTCVideoSource的实现类

    mOutputStream.setSource(new IRCRTCVideoSource() {
      @Override
      public void onInit(IRCVideoConsumer observer) {
        videoConsumer = observer;
      }

      @Override
      public void onStart() {
        //建议在此处初始化自定义视频的采集步骤
      }

      @Override
      public void onStop() {
        videoConsumer = null;
        //建议在此处处理自定义视频采集的停止步骤
      }

      @Override
      public void onDispose() {
        //销毁自定义视频Stream时，此方法回被回调
      }
    });
    room.getLocalUser().publishStream(mOutputStream, new IRCRTCResultCallback() {
      @Override
      public void onSuccess() {

      }

      @Override
      public void onFailed(RTCErrorCode errorCode) {

      }
    });
    byte[] bytes = null;
    int width = 0;
    int height = 0;
    int oesTextureId = 0;
    float[] transformMatrix = null;
    int rotation = 0;
    long timestamp = 0;
    //byteBuffer
    videoConsumer.writeYuvData(bytes, width, height, rotation);
    //或者 texture
    videoConsumer.writeTexture(width, height, oesTextureId, transformMatrix, rotation, timestamp);
  }

  private void registerStatusReportListener() {
    RCRTCEngine.getInstance().registerStatusReportListener(new IRCRTCStatusReportListener(){

      @Override
      public void onAudioReceivedLevel(HashMap<String, String> audioLevel) {
        super.onAudioReceivedLevel(audioLevel);
      }

      @Override
      public void onAudioInputLevel(String audioLevel) {
        super.onAudioInputLevel(audioLevel);
      }

      @Override
      public void onConnectionStats(StatusReport statusReport) {
        super.onConnectionStats(statusReport);
      }
    });
  }

  private void registerAudioBufferListener() {
    room.getLocalUser().getDefaultAudioStream().setAudioDataListener(new IRCRTCAudioDataListener() {
      @Override
      public byte[] onAudioFrame(RCRTCAudioFrame rcrtcAudioFrame) {
        return new byte[0];
      }
    });
    room.setRemoteAudioDataListener(new IRCRTCAudioDataListener() {
      @Override
      public byte[] onAudioFrame(RCRTCAudioFrame rcrtcAudioFrame) {
        return new byte[0];
      }
    });
  }

  private void registerLocalVideoBufferListener() {
    room.getLocalUser().getDefaultVideoStream().setVideoFrameListener(true,
        new IRCRTCVideoOutputFrameListener() {
          @Override
          public RCRTCVideoFrame processVideoFrame(RCRTCVideoFrame rcrtcVideoFrame) {
            return null;
          }
        });
  }

  private void setRoomAttribute() {
    //IM自定义消息
    MessageContent messageContent;
    room.setRoomAttributeValue("key", "value", null, new IRCRTCResultCallback() {
      @Override
      public void onSuccess() {

      }

      @Override
      public void onFailed(RTCErrorCode rtcErrorCode) {

      }
    });
  }

  private void setLocalUserAttribute() {
    //IM自定义消息
    MessageContent messageContent = null;
    room.getLocalUser()
        .setAttributeValue("key", "value", messageContent, new IRCRTCResultCallback() {
          @Override
          public void onSuccess() {

          }

          @Override
          public void onFailed(RTCErrorCode rtcErrorCode) {

          }
        });
  }

}
