package moe.yukinoneko.gcomic.download;

import android.content.Context;
import android.text.TextUtils;
import android.util.SparseArray;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadConnectListener;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloadSampleListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.model.FileDownloadStatus;
import com.liulishuo.filedownloader.util.FileDownloadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import moe.yukinoneko.gcomic.database.GComicDB;
import moe.yukinoneko.gcomic.database.model.DownloadTaskModel;
import moe.yukinoneko.gcomic.network.GComicApi;
import rx.Observable;

/**
 * Created by SamuelGjk on 2016/5/12.
 */
public class DownloadTasksManager {

    // @formatter:off
    private static DownloadTasksManager mInstance;

    private Context context;

    public static DownloadTasksManager getInstance(Context context) {
        if (mInstance == null) {
            synchronized (DownloadTasksManager.class) {
                if (mInstance == null) {
                    mInstance = new DownloadTasksManager(context.getApplicationContext());
                }
            }
        }
        return mInstance;
    }

    private DownloadTasksManager(Context context) {
        this.context = context;
    }

    private SparseArray<BaseDownloadTask> taskSparseArray = new SparseArray<>();

    public void addTaskForViewHolder(final BaseDownloadTask task) {
        taskSparseArray.put(task.getId(), task);
    }

    public void removeTaskForViewHolder(final int id) {
        taskSparseArray.remove(id);
    }

    public void updateViewHolder(final int id, final Object holder) {
        final BaseDownloadTask task = taskSparseArray.get(id);
        if (task == null) {
            return;
        }

        task.setTag(holder);
    }

    public void releaseTask() {
        taskSparseArray.clear();
    }

    public void onDestroy() {
        releaseTask();
        FileDownloader.getImpl().pauseAll();
        FileDownloader.getImpl().unBindService();
    }

    public boolean isReady() {
        return FileDownloader.getImpl().isServiceConnected();
    }

    public Observable<ArrayList<DownloadTaskModel>> getDownloadedComic() {
        return GComicDB.getInstance(context).querySpecificColumnsAndDistinct(DownloadTaskModel.class, new String[]{ "comicId", "comicTitle", "comicCover" });
    }

    public Observable<ArrayList<DownloadTaskModel>> getTasksByComicId(String[] values) {
        return GComicDB.getInstance(context).queryByWhereAndDesc(DownloadTaskModel.class, "comicId", values, "chapterId");
    }

    public void bindService() {
        FileDownloader.getImpl().bindService();
    }

    public void unbindService() {
        FileDownloader.getImpl().unBindService();
    }

    public void addServiceConnectListener(FileDownloadConnectListener listener) {
        FileDownloader.getImpl().addServiceConnectListener(listener);
    }

    public void removeServiceConnectListener(FileDownloadConnectListener listener) {
        FileDownloader.getImpl().removeServiceConnectListener(listener);
    }

    /**
     * @param status Download Status
     * @return has already downloaded
     * @see FileDownloadStatus
     */
    public boolean isDownloaded(final int status) {
        return status == FileDownloadStatus.completed;
    }

    public boolean isDownloadedById(final int id) {
        return getStatus(id) == FileDownloadStatus.completed;
    }

    public boolean isExist(String path) {
        return new File(path).exists();
    }

    public int getStatus(final int id) {
        return FileDownloader.getImpl().getStatus(id);
    }

    public long getTotal(final int id) {
        return FileDownloader.getImpl().getTotal(id);
    }

    public long getSoFar(final int id) {
        return FileDownloader.getImpl().getSoFar(id);
    }

    public Observable<Long> addTask(final DownloadTaskModel model) {

        model.path = generatePath(model.url);
        model.id = FileDownloadUtils.generateId(model.url, model.path);

        return GComicDB.getInstance(context).insert(model);
    }

    public void startTask(final DownloadTaskModel model) {
        int status = getStatus(model.id);
        if (!(status == FileDownloadStatus.paused || status == FileDownloadStatus.error)) {
            return;
        }

        BaseDownloadTask task = FileDownloader.getImpl()
                                              .create(model.url)
                                              .setPath(model.path)
                                              .addHeader("Referer", GComicApi.REFERER)
                                              .setCallbackProgressTimes(100)
                                              .setListener(taskDownloadListener);
        addTaskForViewHolder(task);
        task.start();
    }

    public void pauseTask(int downloadId) {
        FileDownloader.getImpl().pause(downloadId);
    }

    public void startAllTasks(final List<DownloadTaskModel> taskModels) {
        for (DownloadTaskModel taskModel : taskModels) {
            startTask(taskModel);
        }
    }

    public void pauseAllTasks(final List<DownloadTaskModel> taskModels) {
        for (DownloadTaskModel taskModel : taskModels) {
            pauseTask(taskModel.id);
        }
    }

    public String generatePath(final String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        return FileDownloadUtils.getDefaultSaveFilePath(url);
    }

    private FileDownloadListener taskDownloadListener = new FileDownloadSampleListener() {

        private ITaskViewHolder checkCurrentHolder(final BaseDownloadTask task) {
            final ITaskViewHolder tag = (ITaskViewHolder) task.getTag();
            if (tag == null || tag.getId() != task.getId()) {
                return null;
            }

            return tag;
        }

        @Override
        protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            super.pending(task, soFarBytes, totalBytes);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateDownloading(FileDownloadStatus.pending, soFarBytes, totalBytes);
        }

        @Override
        protected void started(BaseDownloadTask task) {
            super.started(task);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateDownloading(FileDownloadStatus.started, 0, 1);
        }

        @Override
        protected void connected(BaseDownloadTask task, String etag, boolean isContinue, int soFarBytes, int totalBytes) {
            super.connected(task, etag, isContinue, soFarBytes, totalBytes);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateDownloading(FileDownloadStatus.connected, soFarBytes, totalBytes);
        }

        @Override
        protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            super.progress(task, soFarBytes, totalBytes);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateDownloading(FileDownloadStatus.progress, soFarBytes, totalBytes);
        }

        @Override
        protected void error(BaseDownloadTask task, Throwable e) {
            super.error(task, e);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateNotDownloaded(FileDownloadStatus.error, task.getLargeFileSoFarBytes(), task.getLargeFileTotalBytes());
            removeTaskForViewHolder(task.getId());
        }

        @Override
        protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            super.paused(task, soFarBytes, totalBytes);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateNotDownloaded(FileDownloadStatus.paused, soFarBytes, totalBytes);
            removeTaskForViewHolder(task.getId());
        }

        @Override
        protected void completed(BaseDownloadTask task) {
            super.completed(task);
            final ITaskViewHolder tag = checkCurrentHolder(task);
            if (tag == null) {
                return;
            }

            tag.updateDownloaded();
            removeTaskForViewHolder(task.getId());
        }
    };

    public interface ITaskViewHolder {

        int getId();

        void updateDownloaded();

        void updateNotDownloaded(final int status, final long sofar, final long total);

        void updateDownloading(final int status, final long sofar, final long total);
    }
}