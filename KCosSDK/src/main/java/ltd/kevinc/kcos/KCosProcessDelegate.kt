package ltd.kevinc.kcos

import android.util.Log
import java.io.File

interface KCosProcessDelegate {
    /**
     * 进行上传时，每成功发出一个数据包，就会触发一次该回调
     * 用currentStep / totalSteps即可得到完成百分比
     * 注意，在继续上传功能中，不会触发这个回调，下方有解释
     */
    fun onUploadTick(currentStep: Long, totalSteps: Long) {

    }

    /**
     * 当使用继续上传功能时，每上传一个数据包，就会触发一次这个回调
     * 该方法无法显示进度百分比
     * 原因是在继续上传时，服务端必须得计算缺失的数据包，才能进行重传
     * 而缺失的数据包ID并不一定等于当前进行中的最后一个数据包的ID，因此无法计算准确的百分比
     */
    fun onContinueUploadTick(currentStep: Long) {

    }

    fun onConversionSuccess(targetFile: File) {
        targetFile.deleteOnExit()
    }

    fun onError(e: Throwable) {
        Log.e("KCos.upload", e.stackTraceToString())
    }
}