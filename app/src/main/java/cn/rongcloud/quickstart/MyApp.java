package cn.rongcloud.quickstart;

import android.app.Application;
import io.rong.imlib.RongIMClient;

public class MyApp extends Application {

  public static final String appkey = "z3v5yqkbv8v30";
  public static final String token1 = "PRP809heER0M401xyD57JdjfPcAu/85xJcX8mJm31uc2Ac1rbd3ROsiCBUeN4JSHV1PP3fn9R1GWCKQ1ArW7gbD2AIq2gaoG1ygZPRwSrbz9n32/MKASgQ==";
  public static final String token2 = "RmcKcQkBgsww5v2ELrlv/R70ERb7EyF45eMRhBBD6OVqhjncZNNvBS0oG4JelG+8Y5rn/ZbQTVodAa3PzTKlVz/gi3hnt8shFz/cSymPS14MELBfm9wF2A==";

  @Override
  public void onCreate() {
    super.onCreate();
    RongIMClient.init(this, appkey, false);
  }
}
