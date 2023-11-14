package io.openim.android.ouimeeting;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.alibaba.android.arouter.facade.annotation.Route;
import com.hjq.permissions.Permission;
import com.hjq.window.EasyWindow;

import java.util.List;

import io.livekit.android.room.track.VideoTrack;
import io.openim.android.ouicore.adapter.RecyclerViewAdapter;
import io.openim.android.ouicore.base.BaseActivity;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.vm.injection.Easy;
import io.openim.android.ouicore.utils.ActivityManager;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.OnDedrepClickListener;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.ouicore.utils.TimeUtil;
import io.openim.android.ouicore.widget.WaitDialog;
import io.openim.android.ouimeeting.databinding.ActivityMeetingLaunchBinding;
import io.openim.android.ouimeeting.databinding.MeetingHomeIietmMemberBinding;
import io.openim.android.ouimeeting.vm.MeetingVM;
import io.openim.android.sdk.models.MeetingInfo;

@Route(path = Routes.Meeting.LAUNCH)
public class MeetingLaunchActivity extends BaseActivity<MeetingVM, ActivityMeetingLaunchBinding> implements MeetingVM.Interaction {

    private RecyclerViewAdapter<MeetingInfo, MeetingItemViewHolder> adapter;
    private WaitDialog waitDialog;
    private HasPermissions shoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        vm = Easy.installVM(MeetingVM.class);
        super.onCreate(savedInstanceState);
        bindViewDataBinding(ActivityMeetingLaunchBinding.inflate(getLayoutInflater()));
        shoot = new HasPermissions(this, Permission.RECORD_AUDIO, Permission.CAMERA);
        init();
        initView();
        listener();
    }

    private void listener() {
        view.timing.setOnClickListener(v -> {
            //这里有可能被释放 所以需要重新放入
            BaseApp.inst().putVM(vm);
            shoot.safeGo(() -> startActivity(new Intent(MeetingLaunchActivity.this,
                TimingMeetingActivity.class)));
        });
        view.timely.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                createWait();
                vm.fastMeeting();
            }
        });
        view.join.setOnClickListener(new OnDedrepClickListener() {
            @Override
            public void click(View v) {
                //这里有可能被释放 所以需要重新放入
                BaseApp.inst().putVM(vm);
                shoot.safeGo(() -> startActivity(new Intent(MeetingLaunchActivity.this,
                    TimingMeetingActivity.class)));
            }
        });
        vm.meetingInfoList.observe(this, meetingInfos -> {
            adapter.setItems(meetingInfos);
        });
    }

    @Override
    public void onError(String error) {
        waitDialog.dismiss();
        toast(error);
    }


    @Override
    public void onSuccess(Object body) {
        waitDialog.dismiss();
        vm.getMeetingInfoList();
        vm.second = 0;
        //这里有可能被释放 所以需要重新放入
        BaseApp.inst().putVM(vm);
        shoot.safeGo(() -> meetingHomeActivityCallBack.launch(new Intent(MeetingLaunchActivity.this,
            MeetingHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    }

    private ActivityResultLauncher<Intent> meetingHomeActivityCallBack =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                vm.getMeetingInfoList();
            }
        });

    private void initView() {
        view.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        view.recyclerView.setAdapter(adapter = new RecyclerViewAdapter<MeetingInfo,
            MeetingItemViewHolder>(MeetingItemViewHolder.class) {

            @SuppressLint("SetTextI18n")
            @Override
            public void onBindView(@NonNull MeetingItemViewHolder holder, MeetingInfo data,
                                   int position) {
                try {
                    holder.view.title.setText(data.getMeetingName());
                    boolean isStart = data.getStartTime() <= (System.currentTimeMillis() / 1000L);
                    holder.view.status.setText(getString(isStart ?
                        io.openim.android.ouicore.R.string.have_begun :
                        io.openim.android.ouicore.R.string.not_started));
                    holder.view.status.setBackgroundResource(isStart ?
                        io.openim.android.ouicore.R.drawable.sty_radius_3_ffffb300 :
                        io.openim.android.ouicore.R.drawable.sty_radius_3_ff0089ff);

                    //相同的id 获取用户信息返回只有一条数据
                    String name;
                    if (vm.userInfos.size() - 1 >= position) {
                        name = vm.userInfos.get(position).getNickname();
                    } else name = vm.userInfos.get(vm.userInfos.size() - 1).getNickname();

                    holder.view.description.setText(TimeUtil.getTime(data.getStartTime() * 1000,
                        TimeUtil.yearMonthDayFormat) + "\t\t\t" + TimeUtil.getTime(data.getStartTime() * 1000, TimeUtil.hourTimeFormat) + "-"
                        + TimeUtil.getTime(data.getEndTime() * 1000, TimeUtil.hourTimeFormat) +
                        "\t\t\t"
                        + String.format(getString(io.openim.android.ouicore.R.string.initiator),
                        name));


                    holder.view.join.setOnClickListener(v -> {
                        //这里有可能被释放 所以需要重新放入
                        BaseApp.inst().putVM(vm);
                        vm.selectMeetingInfo = data;
                        shoot.safeGo(() -> startActivity(new Intent(MeetingLaunchActivity.this,
                            MeetingDetailActivity.class)));

                    });
                } catch (Exception ignored) {

                }
            }
        });

    }

    private void createWait() {
        waitDialog = new WaitDialog(MeetingLaunchActivity.this);
        waitDialog.show();
    }

    void init() {
        vm.getMeetingInfoList();
    }

    @Override
    public void connectRoomSuccess(VideoTrack localVideoTrack) {

    }

    @Override
    protected void fasterDestroy() {
        if (null == ActivityManager.isExist(MeetingHomeActivity.class))
            Easy.delete(MeetingVM.class);
    }

    public static class MeetingItemViewHolder extends RecyclerView.ViewHolder {
        public final MeetingHomeIietmMemberBinding view;

        public MeetingItemViewHolder(@NonNull View itemView) {
            super(MeetingHomeIietmMemberBinding.inflate(LayoutInflater.from(itemView.getContext()), (ViewGroup) itemView, false).getRoot());
            view = MeetingHomeIietmMemberBinding.bind(this.itemView);
        }
    }

}
