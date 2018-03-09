package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import xyz.monkeytong.hongbao.utils.HongbaoSignature;
import xyz.monkeytong.hongbao.utils.PowerUtil;

public class HongbaoService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String WECHAT_DETAILS_EN = "Details";
    private static final String WECHAT_DETAILS_CH = "绑定的微信零钱账户";
    private static final String WECHAT_BETTER_LUCK_EN = "Better luck next time!";
    private static final String WECHAT_BETTER_LUCK_CH = "手慢了";
    private static final String WECHAT_EXPIRES_CH = "已超过24小时";
    private static final String WECHAT_VIEW_SELF_CH = "查看红包";
    private static final String WECHAT_VIEW_OTHERS_CH = "领取红包";
    private static final String WECHAT_NOTIFICATION_TIP = "[红包]";
    private static final String WECHAT_LUCKMONEY_RECEIVE_ACTIVITY = "RedEnvelopeCollectorActivity";//com.tencent.wework/.msg.controller.MessageListActivity
    private static final String WECHAT_LUCKMONEY_DETAIL_ACTIVITY = "RedEnvelopeDetailActivity";
    private static final String WECHAT_LUCKMONEY_GENERAL_ACTIVITY = "MessageListActivity";
    private static final String WECHAT_CHATTING_ACTIVITY = "MessageListActivity";
    private static final String WECHAT_MESSAGE_LIST_ACTIVITY = "WwMainActivity";
    private String currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
    private static final  String TAG = "----------zwf";
    private int exu_count = 0;

    private AccessibilityNodeInfo rootWindowInfo, mReceiveNode, mUnpackNode;
    private boolean mClickPackage, mLuckyMoneyReceived;
    private int mUnpackCount = 0;
    private boolean mMutex = false, mListMutex = false, mChatMutex = false, mOpeningPackage = false;
    private HongbaoSignature signature = new HongbaoSignature();

    private PowerUtil powerUtil;
    private SharedPreferences sharedPreferences;

    /**
     * AccessibilityEvent
     *
     * @param event 事件
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (sharedPreferences == null) return;
        setCurrentActivityName(event);
        switch (event.getEventType()){
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
//                Log.v(TAG, "窗口变化变化！！！");
                watchChat(event);
                break;
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:

//                Log.v(TAG, "通知栏变化！！！");
                watchNotifications(event);

                break;
            default:
                break;

        }



//        setCurrentActivityName(event);
//        /* 检测通知消息 */
//        if (!mMutex) {
//            //监测通知栏，有[红包]就打开通知
//            if (watchNotifications(event)) return;
////            if  (watchList(event)) return;
//            mListMutex = false;
//        }
//
//        if (!mChatMutex) {
//            mChatMutex = true;
//            watchChat(event);
//            mChatMutex = false;
//        }
    }

    private void watchChat(AccessibilityEvent event) {
        if (mOpeningPackage) return;
        this.rootWindowInfo = getRootInActiveWindow();

        if (rootWindowInfo == null) return;

        mReceiveNode = null;
        mUnpackNode = null;

        //监测是否是正常红包
        if (!this.openPackageMainLayer()) return;

        checkPackageEnable(event.getEventType());

        /* 如果戳开但还未领取 */
        if (mUnpackCount == 1 && (mUnpackNode != null)) {
            int delayFlag = 10;
            mOpeningPackage = true;
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            try {
                                openPacket();
                                mOpeningPackage = false;
                                mClickPackage = false;
                            } catch (Exception e) {
                                mClickPackage = false;
                                mUnpackCount = 0;
                            }
                        }
                    },
                    delayFlag);
        }
    }

    private void openPacket() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float dpi = metrics.density;
        if (android.os.Build.VERSION.SDK_INT <= 23) {
            mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            if (android.os.Build.VERSION.SDK_INT > 23) {

                Path path = new Path();
                if (640 == dpi) {
                    path.moveTo(720, 1575);
                } else {
                    path.moveTo(540, 1060);
                }
                GestureDescription.Builder builder = new GestureDescription.Builder();
                GestureDescription gestureDescription = builder.addStroke(new GestureDescription.StrokeDescription(path, 450, 50)).build();
                dispatchGesture(gestureDescription, new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        Log.d("test", "onCompleted");
                        mMutex = false;
                        super.onCompleted(gestureDescription);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        Log.d("test", "onCancelled");
                        mMutex = false;
                        super.onCancelled(gestureDescription);
                    }
                }, null);

            }
        }
    }

    private void setCurrentActivityName(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        try {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );
            Log.v(TAG, "currentActivityName: "+componentName.flattenToShortString());
            getPackageManager().getActivityInfo(componentName, 0);
            currentActivityName = componentName.flattenToShortString();
        } catch (PackageManager.NameNotFoundException e) {
            currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
        }
    }

    private boolean watchList(AccessibilityEvent event) {
        if (mListMutex) return false;
        mListMutex = true;
        AccessibilityNodeInfo eventSource = event.getSource();
        // Not a message
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventSource == null)
            return false;

        List<AccessibilityNodeInfo> nodes = eventSource.findAccessibilityNodeInfosByText(WECHAT_NOTIFICATION_TIP);
        //增加条件判断currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)
        //避免当订阅号中出现标题为“[微信红包]拜年红包”（其实并非红包）的信息时误判
        if (!nodes.isEmpty() && currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)) {
            AccessibilityNodeInfo nodeToClick = nodes.get(0);
            if (nodeToClick == null) return false;
            CharSequence contentDescription = nodeToClick.getContentDescription();
            if (contentDescription != null && !signature.getContentDescription().equals(contentDescription)) {
                nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                signature.setContentDescription(contentDescription.toString());
                return true;
            }
        }
        return false;
    }

    //监听通知栏
    private boolean watchNotifications(AccessibilityEvent event) {
        // Not a notification
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
            return false;

        // Not a hongbao
        String tip = event.getText().toString();
        Log.v(TAG, "tip: "+tip);
        if (!tip.contains(WECHAT_NOTIFICATION_TIP)) return true;

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                /* 清除signature,避免进入会话后误判 */
                signature.cleanSignature();

                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void onInterrupt() {

    }

    private AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node) {
        if (node == null)
            return null;

//        List<AccessibilityNodeInfo> buttons = node.findAccessibilityNodeInfosByViewId("com.tencent.wework:id/bv3");
//        if (buttons.size()!=0){
//            Log.v(TAG, "==========buttons"+buttons.get(0).getClassName());
//            return  buttons.get(0);
//        }
////        com.tencent.wework:id/bv3
//        if ("android.widget.ImageView".equals(node.getClassName()))
//        {
//            AccessibilityNodeInfo parent = node.getParent();
//            if (parent.getChildCount() == 2)
//            {
//                Log.v(TAG, "==================="+exu_count+"=====================");
//                Log.v(TAG, "name: "+node.getClassName());
//                Log.v(TAG, "count: "+ node.getChildCount());
//                Log.v(TAG, "isClickable: "+node.isClickable());
//                Log.v(TAG, "parent childCount: "+parent.getChildCount());
//                Log.v(TAG, "==================="+exu_count+"=====================\n");
//                return node;
//            }
//
//        }
        Log.v(TAG, "currentActivityName:::::::"+currentActivityName);
        if (!currentActivityName.contains("RedEnvelopeCollectorActivity"))
        {
            return null;
        }
        Log.v(TAG, "currentActivityName::::::::"+currentActivityName);

        List<AccessibilityNodeInfo> buttons = node.findAccessibilityNodeInfosByViewId("com.tencent.wework:id/bv3");
        if (buttons.size()!=0){
            Log.v(TAG, "==========buttons"+buttons.get(0).getClassName());
            return  buttons.get(0);
        }
//        if (node.getChildCount() == 0) {
//            if ("android.widget.ImageView".equals(node.getClassName()))
//                return node;
//            else
//                return null;
//        }

        //layout元素，遍历找button
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null)
                return button;
        }
        return null;
    }
    /*
        打开红包界面
     */
    private boolean openPackageMainLayer(){
        if (mClickPackage) return true;
        /* 聊天会话窗口，遍历节点匹配“领取红包”和"查看红包" */
        AccessibilityNodeInfo mReceiveNode = this.getTheLastNode(WECHAT_VIEW_OTHERS_CH);
        if (mReceiveNode != null &&
                (currentActivityName.contains(WECHAT_CHATTING_ACTIVITY)
                        || currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY))) {
            mClickPackage = true;
            mReceiveNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);

            return true;
        }else{
            //未找到红包
            Log.v(TAG, "该界面没有红包");
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
        return false;
    }

    private void checkPackageEnable(int eventType) {
        if (this.rootWindowInfo == null) return;


        /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” 获得拆红包按钮*/
        AccessibilityNodeInfo node2 = findOpenButton(this.rootWindowInfo);
        if (node2 != null && "android.widget.ImageView".equals(node2.getClassName()) ) {
            mUnpackNode = node2;
            mUnpackCount += 1;
            return;
        }
        Log.v(TAG, "mUnpackCount:"+mUnpackCount);

        /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了” */
        boolean hasNodes = this.hasOneOfThoseNodes(
                WECHAT_BETTER_LUCK_CH, WECHAT_DETAILS_CH,
                WECHAT_BETTER_LUCK_EN, WECHAT_DETAILS_EN, WECHAT_EXPIRES_CH);
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && hasNodes
                && (currentActivityName.contains(WECHAT_LUCKMONEY_DETAIL_ACTIVITY)
                || currentActivityName.contains(WECHAT_LUCKMONEY_RECEIVE_ACTIVITY))) {
            mClickPackage = false;
            mUnpackCount = 0;
            performGlobalAction(GLOBAL_ACTION_BACK);
            signature.commentString = generateCommentString();
        }


        return;
    }

    private void sendComment() {
        try {
            AccessibilityNodeInfo outNode =
                    getRootInActiveWindow().getChild(0).getChild(0);
            AccessibilityNodeInfo nodeToInput = outNode.getChild(outNode.getChildCount() - 1).getChild(0).getChild(1);

            if ("android.widget.EditText".equals(nodeToInput.getClassName())) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, signature.commentString);
                nodeToInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }
        } catch (Exception e) {
            // Not supported
        }
    }


    private boolean hasOneOfThoseNodes(String... texts) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootWindowInfo.findAccessibilityNodeInfosByText(text);

            if (nodes.size()!=0) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootWindowInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) return null;
                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
                    signature.others = text.equals(WECHAT_VIEW_OTHERS_CH);
                }
            }
        }
        return lastNode;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        this.watchFlagsFromPreference();
    }

    private void watchFlagsFromPreference() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        this.powerUtil = new PowerUtil(this);
        Boolean watchOnLockFlag = sharedPreferences.getBoolean("pref_watch_on_lock", false);
        this.powerUtil.handleWakeLock(watchOnLockFlag);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_watch_on_lock")) {
            Boolean changedValue = sharedPreferences.getBoolean(key, false);
            this.powerUtil.handleWakeLock(changedValue);
        }
    }

    @Override
    public void onDestroy() {
        this.powerUtil.handleWakeLock(false);
        super.onDestroy();
    }

    private String generateCommentString() {
        if (!signature.others) return null;

        Boolean needComment = sharedPreferences.getBoolean("pref_comment_switch", false);
        if (!needComment) return null;

        String[] wordsArray = sharedPreferences.getString("pref_comment_words", "").split(" +");
        if (wordsArray.length == 0) return null;

        Boolean atSender = sharedPreferences.getBoolean("pref_comment_at", false);
        if (atSender) {
            return "@" + signature.sender + " " + wordsArray[(int) (Math.random() * wordsArray.length)];
        } else {
            return wordsArray[(int) (Math.random() * wordsArray.length)];
        }
    }
}
