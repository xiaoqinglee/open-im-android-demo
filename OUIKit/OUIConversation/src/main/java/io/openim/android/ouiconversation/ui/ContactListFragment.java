package io.openim.android.ouiconversation.ui;


import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import com.alibaba.android.arouter.facade.annotation.Route;
import com.yanzhenjie.recyclerview.SwipeMenuCreator;
import com.yanzhenjie.recyclerview.SwipeMenuItem;

import java.util.List;

import io.openim.android.ouiconversation.R;

import io.openim.android.ouiconversation.databinding.FragmentContactListBinding;
import io.openim.android.ouiconversation.vm.ChatVM;
import io.openim.android.ouicore.vm.ContactListVM;
import io.openim.android.ouicore.adapter.ViewHol;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.BaseFragment;
import io.openim.android.ouicore.entity.MsgConversation;
import io.openim.android.ouicore.im.IMUtil;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constant;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.ouicore.utils.TimeUtil;
import io.openim.android.sdk.OpenIMClient;

@Route(path = Routes.Conversation.CONTACT_LIST)
public class ContactListFragment extends BaseFragment<ContactListVM> implements ContactListVM.ViewAction {

    private long mLastClickTime;
    private long timeInterval = 700;

    private FragmentContactListBinding view;
    private CustomAdapter adapter;

    public static ContactListFragment newInstance() {
        return new ContactListFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        bindVM(ContactListVM.class);
        BaseApp.inst().putVM(vm);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        view = FragmentContactListBinding.inflate(getLayoutInflater());
        init();
        return view.getRoot();
    }


    @SuppressLint("NewApi")
    private void init() {
        view.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SwipeMenuCreator mSwipeMenuCreator = (leftMenu, rightMenu, position) -> {
            SwipeMenuItem delete = new SwipeMenuItem(getContext());
            delete.setText(R.string.remove);
            delete.setHeight(MATCH_PARENT);
            delete.setWidth(Common.dp2px(73));
            delete.setTextSize(16);
            delete.setTextColor(getContext().getColor(android.R.color.white));
            delete.setBackgroundColor(Color.parseColor("#FFAB41"));

            MsgConversation conversationInfo = vm.conversations.getValue().get(position);
            SwipeMenuItem top = new SwipeMenuItem(getContext());
            top.setText(conversationInfo
                .conversationInfo.isPinned() ?
                io.openim.android.ouicore.R.string.cancel_top : R.string.top);
            top.setHeight(MATCH_PARENT);
            top.setWidth(Common.dp2px(73));
            top.setTextSize(16);
            top.setTextColor(getContext().getColor(android.R.color.white));
            top.setBackgroundColor(Color.parseColor("#1B72EC"));

            SwipeMenuItem martRead = new SwipeMenuItem(getContext());
            martRead.setText(io.openim.android.ouicore.R.string.mark_read);
            martRead.setHeight(MATCH_PARENT);
            martRead.setWidth(Common.dp2px(73));
            martRead.setTextSize(16);
            martRead.setTextColor(getContext().getColor(android.R.color.white));
            martRead.setBackgroundColor(Color.parseColor("#C9C9C9"));

            //右侧添加菜单
            rightMenu.addMenuItem(top);
            if (conversationInfo.conversationInfo.getUnreadCount() > 0)
                rightMenu.addMenuItem(martRead);
            rightMenu.addMenuItem(delete);
        };
        view.recyclerView.setSwipeMenuCreator(mSwipeMenuCreator);
        ChatVM chatVM = new ChatVM();
        view.recyclerView.setOnItemMenuClickListener((menuBridge, adapterPosition) -> {
            int menuPosition = menuBridge.getPosition();
            MsgConversation conversationInfo = vm.conversations.getValue().get(adapterPosition);
            if (menuPosition == 0) {
                vm.pinConversation(conversationInfo.conversationInfo,
                    !conversationInfo.conversationInfo.isPinned());
            } else if (menuPosition == 1 && conversationInfo.conversationInfo.getUnreadCount() > 0) {
                chatVM.markReadedByConID(conversationInfo.conversationInfo.getConversationID(),
                    null, null);
            } else {
                vm.conversations.getValue().remove(conversationInfo);
                adapter.notifyItemRemoved(adapterPosition);
                vm.deleteConversationFromLocalAndSvr(conversationInfo.conversationInfo.getConversationID());
            }
            menuBridge.closeMenu();
        });
        view.recyclerView.setOnItemClickListener((view, position) -> {
            long nowTime = System.currentTimeMillis();
            if (nowTime - mLastClickTime < timeInterval)
                return;
            mLastClickTime = nowTime;

            MsgConversation msgConversation = vm.conversations.getValue().get(position);
            if (msgConversation.conversationInfo.getConversationType() == Constant.SessionType.NOTIFICATION) {
                //系统通知
                Intent intent = new Intent(getContext(), NotificationActivity.class)
                    .putExtra(Constant.K_NAME, msgConversation.conversationInfo.getShowName())
                    .putExtra(Constant.K_ID, msgConversation.conversationInfo.getConversationID());
                startActivity(intent);
                return;
            }
            Intent intent = new Intent(getContext(), ChatActivity.class)
                .putExtra(Constant.K_NAME, msgConversation.conversationInfo.getShowName());
            if (msgConversation.conversationInfo.getConversationType() == Constant.SessionType.SINGLE_CHAT)
                intent.putExtra(Constant.K_ID, msgConversation.conversationInfo.getUserID());

            if (msgConversation.conversationInfo.getConversationType() == Constant.SessionType.GROUP_CHAT
                || msgConversation.conversationInfo.getConversationType() == Constant.SessionType.SUPER_GROUP)
                intent.putExtra(Constant.K_GROUP_ID, msgConversation.conversationInfo.getGroupID());

            if (msgConversation.conversationInfo.getGroupAtType() == Constant.SessionType.NOTIFICATION)
                intent.putExtra(Constant.K_NOTICE, msgConversation.notificationMsg);
            startActivity(intent);

            //重置强提醒
            OpenIMClient.getInstance().conversationManager.resetConversationGroupAtType(null, msgConversation.conversationInfo.getConversationID());
        });

        adapter = new CustomAdapter(getContext());
        view.recyclerView.setAdapter(adapter);
        view.recyclerView.addHeaderView(createHeaderView());


//        view.recyclerView.addItemDecoration(new DefaultItemDecoration(getActivity().getColor(android.R.color.transparent), 1, 36));
        vm.conversations.observe(getActivity(), v -> {
            if (null == v || v.size() == 0) return;
            adapter.setConversationInfos(v);
            adapter.notifyDataSetChanged();
        });
    }

    private View createHeaderView() {
        View header = getLayoutInflater().inflate(R.layout.view_search, view.recyclerView, false);
        header.setOnClickListener(v -> startActivity(new Intent(getActivity(), SearchActivity.class)));
        return header;
    }


    @Override
    public void onErr(String msg) {
        try {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }catch (Exception ignored){}

    }

    @Override
    public void onSuccess(Object body) {
        super.onSuccess(body);
    }

    static class CustomAdapter extends RecyclerView.Adapter<ViewHol.ContactItemHolder> {

        private List<MsgConversation> conversationInfos;
        private Context context;

        public CustomAdapter(Context context) {
            this.context = context;
        }

        public void setConversationInfos(List<MsgConversation> conversationInfos) {
            this.conversationInfos = conversationInfos;
        }

        public List<MsgConversation> getConversationInfos() {
            return conversationInfos;
        }


        @Override
        public ViewHol.ContactItemHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            return new ViewHol.ContactItemHolder(viewGroup);
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onBindViewHolder(ViewHol.ContactItemHolder viewHolder, final int position) {
            MsgConversation msgConversation = conversationInfos.get(position);

            viewHolder.viewBinding.avatar.load(msgConversation.conversationInfo.getFaceURL(), msgConversation.conversationInfo.getConversationType()
                != Constant.SessionType.SINGLE_CHAT);
            viewHolder.viewBinding.nickName.setText(msgConversation.conversationInfo.getShowName());

            if (msgConversation.conversationInfo.getRecvMsgOpt() != 0) {
                if (msgConversation.conversationInfo.getUnreadCount() > 0)
                    viewHolder.viewBinding.noDisturbTips.setVisibility(View.VISIBLE);
                viewHolder.viewBinding.noDisturbIc.setVisibility(View.VISIBLE);
                viewHolder.viewBinding.badge.badge.setVisibility(View.GONE);
            } else {
                viewHolder.viewBinding.badge.badge.setVisibility(View.VISIBLE);
                viewHolder.viewBinding.noDisturbTips.setVisibility(View.GONE);
                viewHolder.viewBinding.noDisturbIc.setVisibility(View.GONE);
                viewHolder.viewBinding.badge.badge.setVisibility(msgConversation.conversationInfo.getUnreadCount() != 0 ? View.VISIBLE : View.GONE);
                viewHolder.viewBinding.badge.badge.setText(msgConversation.conversationInfo.getUnreadCount() + "");
            }
            viewHolder.viewBinding.time.setText(TimeUtil.getTimeString(msgConversation.conversationInfo.getLatestMsgSendTime()));

            viewHolder.viewBinding.getRoot().setBackgroundColor(
                Color.parseColor(msgConversation.conversationInfo.isPinned()
                    ? "#FFF3F3F3" : "#FFFFFF"));

            String lastMsg = IMUtil.getMsgParse(msgConversation.lastMsg);
            //强提醒
            if (msgConversation.conversationInfo.getGroupAtType() == Constant.GroupAtType.groupNotification) {
                String target = "[" + context.getString(io.openim.android.ouicore.R.string.group_bulletin) + "]";
                try {
                    lastMsg = target + msgConversation.notificationMsg.group.notification;
                } catch (Exception e) {
                    if (!lastMsg.contains(target))
                        lastMsg = target + "\t" + lastMsg;
                }
                Common.stringBindForegroundColorSpan(viewHolder.viewBinding.lastMsg, lastMsg,
                    target, BaseApp.inst()
                        .getColor(android.R.color.holo_red_dark));

            } else if (msgConversation.conversationInfo.getGroupAtType() == Constant.GroupAtType.atMe) {
                String target = "@" + BaseApp.inst()
                    .getString(io.openim.android.ouicore.R.string.you);
                if (!lastMsg.contains(target))
                    lastMsg = target + "\t" + lastMsg;
                Common.stringBindForegroundColorSpan(viewHolder.viewBinding.lastMsg, lastMsg, target, BaseApp.inst()
                    .getColor(android.R.color.holo_red_dark));
            } else
                viewHolder.viewBinding.lastMsg.setText(lastMsg);
        }

        @Override
        public int getItemCount() {
            return null == conversationInfos ? 0 : conversationInfos.size();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BaseApp.viewModels.remove(vm.getClass().getCanonicalName());
    }

}
