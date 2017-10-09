package com.seuic.app.store.ui.presenter;

import android.view.View;

import com.seuic.app.store.AppStoreApplication;
import com.seuic.app.store.bean.RecycleObject;
import com.seuic.app.store.bean.RecycleSummaryBean;
import com.seuic.app.store.bean.RecycleViewType;
import com.seuic.app.store.bean.response.RecommendReceive;
import com.seuic.app.store.greendao.CheckUpdateAppsTable;
import com.seuic.app.store.greendao.GreenDaoManager;
import com.seuic.app.store.net.ApiManager;
import com.seuic.app.store.net.download.DownloadManager;
import com.seuic.app.store.ui.contact.ManagerContent;
import com.seuic.app.store.ui.dialog.DialogManager;
import com.seuic.app.store.utils.AppStoreUtils;
import com.seuic.app.store.utils.AppsUtils;
import com.seuic.app.store.utils.NetworkUtils;
import com.seuic.app.store.utils.RxUtils;
import com.seuic.app.store.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created on 2017/9/17.
 *
 * @author dpuntu
 */

public class ManagerPresenter implements ManagerContent.Presenter {
    private ManagerContent.View mView;
    private boolean isRefresh = false;
    private RecommendReceive recommendReceiveSelf;
    private List<RecommendReceive> mRecommendReceiveList;

    public ManagerPresenter(ManagerContent.View view) {
        mView = view;
    }

    @Override
    public void checkUpdate(boolean isRefresh) {
        this.isRefresh = isRefresh;
        RxUtils.onErrorInterceptor(
                ApiManager.getInstance()
                        .getService()
                        .checkUpdate(AppsUtils.getAppVersionRequests(AppStoreApplication.getApp())))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CheckUpdateObserver("CheckUpdate"));
    }

    @Override
    public void checkGreenDao4Self() {
        List<CheckUpdateAppsTable> checkUpdateAppsTableList = GreenDaoManager.getInstance().queryCheckUpdateApps();
        boolean isUpdate = false;
        if (mRecommendReceiveList != null) {
            mRecommendReceiveList.clear();
            mRecommendReceiveList = new ArrayList<>();
        }
        for (CheckUpdateAppsTable checkUpdateAppsTable : checkUpdateAppsTableList) {
            RecommendReceive recommendReceive = new RecommendReceive(checkUpdateAppsTable.getAppName(),
                                                                     checkUpdateAppsTable.getPackageName(),
                                                                     checkUpdateAppsTable.getAppSize(),
                                                                     checkUpdateAppsTable.getAppVersion(),
                                                                     checkUpdateAppsTable.getAppVersionId(),
                                                                     checkUpdateAppsTable.getAppDesc(),
                                                                     checkUpdateAppsTable.getMD5(),
                                                                     checkUpdateAppsTable.getDownloadName(),
                                                                     checkUpdateAppsTable.getAppIconName());
            if (checkUpdateAppsTable.getPackageName().equals(AppStoreUtils.getAppPackageName())) {
                isUpdate = true;
                mView.updateSelf(recommendReceive);
                updateAllApp();
                break;
            }
            mRecommendReceiveList.add(recommendReceive);
        }

        if (!isUpdate) {
            mView.updateSelf(null);
        }
    }

    private class CheckUpdateObserver extends RxUtils.ResponseObserver<List<RecommendReceive>> {
        CheckUpdateObserver(String observerName) {
            super(observerName);
        }

        @Override
        public void onSuccess(List<RecommendReceive> recommendReceives) {
            boolean isUpdateSelf = false;
            if (recommendReceives != null) {
                for (RecommendReceive recommendReceive : recommendReceives) {
                    if (recommendReceive.getPackageName().equals(AppStoreUtils.getAppPackageName())) {
                        isUpdateSelf = true;
                        recommendReceiveSelf = recommendReceive;
                    }
                }
                if (recommendReceives.size() <= 0) {
                    checkRefresh("", isUpdateSelf, isRefresh);
                } else {
                    checkRefresh(recommendReceives.size() + "", isUpdateSelf, isRefresh);
                }
            }
            if (mRecommendReceiveList != null) {
                mRecommendReceiveList.clear();
                mRecommendReceiveList = null;
            }
            mRecommendReceiveList = recommendReceives;
            GreenDaoManager.getInstance().insertCheckUpdateAppsTableDao(recommendReceives);
        }

        @Override
        public void onError(String errorMsg) {
            checkRefresh("", false, isRefresh);
        }
    }

    private void checkRefresh(String updateCount, boolean isUpdateSelf, boolean isRefresh) {
        if (isUpdateSelf) {
            updateSelf(recommendReceiveSelf);
        }

        updateAllApp();

        if (isRefresh) {
            mView.refreshView(updateCount, isUpdateSelf ? "new" : "");
        } else {
            defaultList(updateCount, isUpdateSelf);
        }
    }

    private void defaultList(String updateCount, boolean isUpdateSelf) {
        List<RecycleObject> mRecycleObjectList = new ArrayList<>();
        mRecycleObjectList.add(new RecycleObject<>(RecycleViewType.MANAGER_APPS_UPDATE, new RecycleSummaryBean("应用更新", "", updateCount)));
        mRecycleObjectList.add(new RecycleObject<>(RecycleViewType.MANAGER_INSTALL, new RecycleSummaryBean("安装管理", "", "")));
        mRecycleObjectList.add(new RecycleObject<>(RecycleViewType.MANAGER_AUTO_UPDATE, new RecycleSummaryBean("自动更新", "WIFI环境下自动更新应用", "")));
        mRecycleObjectList.add(new RecycleObject<>(RecycleViewType.MANAGER_UPDATE_SELF, new RecycleSummaryBean("检测新版本", AppStoreUtils.getAppVersion(), isUpdateSelf ? "new" : "")));
        mView.updateView(mRecycleObjectList);
    }

    public void updateSelf(final RecommendReceive recommendReceive) {
        DialogManager.getInstance()
                .showHintDialog(recommendReceive.getAppName() + "版本更新提示",
                                recommendReceive.getAppDesc(),
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        DialogManager.getInstance().dismissHintDialog();
                                        DownloadManager.getInstance().add2OkhttpDownloaderMap(recommendReceive);
                                        DownloadManager.getInstance().start(recommendReceive.getAppVersionId());
                                    }
                                },
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        DialogManager.getInstance().dismissHintDialog();
                                    }
                                });
    }

    // wifi环境自动更新判断
    private void updateAllApp() {
        if (mRecommendReceiveList != null &&
                mRecommendReceiveList.size() > 0 &&
                (SpUtils.getInstance().getPreferences().getBoolean(SpUtils.SP_SWITCH_AUTO, false)) &&
                NetworkUtils.getNetType() == NetworkUtils.NETTYPE.WIFI_NET) {
            for (RecommendReceive recommendReceive : mRecommendReceiveList) {
                DownloadManager.getInstance().add2OkhttpDownloaderMap(recommendReceive);
                DownloadManager.getInstance().start(recommendReceive.getAppVersionId());
            }
        }
    }
}
