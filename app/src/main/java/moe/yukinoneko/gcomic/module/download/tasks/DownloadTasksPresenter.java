package moe.yukinoneko.gcomic.module.download.tasks;

import android.content.Context;

import java.util.ArrayList;

import moe.yukinoneko.gcomic.base.BasePresenter;
import moe.yukinoneko.gcomic.data.ComicData;
import moe.yukinoneko.gcomic.database.model.DownloadTaskModel;
import moe.yukinoneko.gcomic.download.DownloadTasksManager;
import moe.yukinoneko.gcomic.network.GComicApi;
import moe.yukinoneko.gcomic.utils.Utils;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by SamuelGjk on 2016/5/13.
 */
public class DownloadTasksPresenter extends BasePresenter<IDownloadTasksView> {

    public DownloadTasksPresenter(Context context, IDownloadTasksView iView) {
        super(context, iView);
    }

    void fetchDownloadTasks(int comicId) {
        Subscription subscription = DownloadTasksManager.getInstance(mContext)
                                                        .getTasksByComicId(new String[]{ String.valueOf(comicId) })
                                                        .observeOn(AndroidSchedulers.mainThread())
                                                        .subscribe(new Action1<ArrayList<DownloadTaskModel>>() {
                                                            @Override
                                                            public void call(ArrayList<DownloadTaskModel> downloadTaskModels) {
                                                                iView.updateDownloadTasksList(downloadTaskModels);
                                                            }
                                                        });
        addSubscription(subscription);
    }

    void fetchComicFullChapters(int comicId) {
        if (!Utils.isConnected(mContext)) {
            return;
        }

        Subscription subscription = GComicApi.getInstance()
                                             .fetchComicDetails(comicId)
                                             .observeOn(AndroidSchedulers.mainThread())
                                             .subscribe(new Action1<ComicData>() {
                                                 @Override
                                                 public void call(ComicData comicData) {
                                                     iView.setComicFullChapters(comicData.chapters.get(0).data);
                                                 }
                                             }, new Action1<Throwable>() {
                                                 @Override
                                                 public void call(Throwable throwable) {
                                                     iView.showMessageSnackbar(throwable.getMessage());
                                                 }
                                             });
        addSubscription(subscription);
    }
}