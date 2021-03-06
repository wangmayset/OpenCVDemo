package com.izis.yzext.helper

import android.graphics.RectF
import android.os.Build
import android.support.v4.util.SparseArrayCompat
import android.view.View
import android.widget.Toast
import com.izis.yzext.*
import com.izis.yzext.base.RxBus
import com.izis.yzext.base.RxEvent
import com.izis.yzext.pl2303.LiveType
import com.izis.yzext.pl2303.LogToFile
import com.izis.yzext.pl2303.LogUtils
import com.izis.yzext.pl2303.Pl2303InterfaceUtilNew
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import lxf.widget.tileview.Board
import lxf.widget.tileview.PieceProcess
import lxf.widget.tileview.SgfHelper
import lxf.widget.util.ToastUtils
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 虚拟棋盘
 * Created by lxf on 18-5-29.
 */
class TileHelper(private var pl2303interface: Pl2303InterfaceUtilNew?, private val game: GameInfo, private val errorListener: OnErrorListener) {
    /**
     * 储存死子列表
     */
    var mSparseArray = SparseArrayCompat<List<PieceProcess>>()
    /***
     * 电子棋盘相关
     */
    var commandhead = arrayOf("~STA", // 命令：主动请求全盘数据，返回~STAstasucceed# 和
            // ~SDA111……#）
            "~SDA", // 命令：收到下位机发送的 全盘棋子信息
            "~SIN", // 命令：收到下位机发送来的 单个棋子信息
            "~CMD", // 命令：
            "~PAB", // 命令：
            "~PAW", // 命令：
            "~REB", // 命令：
            "~REW", // 命令：
            "~RNB", // 命令：
            "~RNW", // 命令：
            "~GIB", // 命令：
            "~GIW", // 命令：
            "~ANA", // 命令：
            "~BKY",//黑方棋钟拍下
            "~WKY"//白方棋钟拍下
    )
    var defaultcommandhead = arrayOf("STA", "SDA", "SIN", "CMD", "PAB", "PAW", "REB", "REW", "RNB", "RNW", "GIB", "GIW", "ANA", "BKY", "WKY")
    // 数据头长度
    var startLength = 4
    // 丢失"~"后数据头长度
    var defaultstartLength = 3
    // 数据尾长度
    var endLength = 1
    /**
     * 旋转角度
     */
    var rotate: Int = 0
    /**
     * 是否是中途进入对局的第一帧数据
     */
    private var isMiddleFirst = false


    val data: PlayChessModel = PlayChessModelImpl()
    var view: PlayChessView = PlayChessView(pl2303interface)

    fun readData(readdata: String) {
        val data: String
        var command = ""
        var cmdData = ""

        // readdata 必须是以"~"开头或者指令开头（可能存在丢失数据包的情况），正常情况必然是以“~”开头
        if (readdata.endsWith("#"))
        // 一条完整的指令，粘包情况先不考虑
        {
            data = readdata

            for (i in 0 until commandhead.size) {
                if (data.startsWith(commandhead[i])) {
                    command = data.substring(0, startLength)
                    cmdData = data.substring(startLength, data.length - endLength)
                    break
                } else if (data.startsWith(defaultcommandhead[i])) {
                    command = data.substring(0, defaultstartLength)
                    cmdData = data.substring(defaultstartLength, data.length - endLength)
                    break
                }
            }

            LogToFile.d("pl2303指令", command + cmdData)
            LogUtils.d("pl2303指令", command + cmdData)
            // 收到全盘信息
            when (command) {
                "SDA", "~SDA" -> {
                    // 收到完整的盘面
                    val rotate = rotate()

                    val liveType = try {
                        pl2303interface?.handleReceiveDataRobot(view.board,
                                cmdData, false, rotate)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }

                    if (liveType != null)
                        receiveTileViewMessage(liveType, cmdData)
                }
                "BKY", "WKY", "~BKY", "~WKY" -> {
                    pl2303interface?.WriteToUARTDevice("~STA#")
//                    pl2303interface?.WriteToUARTDevice("~RGC#")
                }
            }

        }
    }

    fun receiveTileViewMessage(value: LiveType, cmdData: String) {
        if (LiveType.LAST_ERROR != value.type && LiveType.LITTLE_ERROR != value.type) {
            //isSound = !SharedPrefsUtil.getValue(context, "sound", false)
            data.soundWarning = false // 在1秒延迟内，设置成false后，发声子线程将不发声。
        }
        LogToFile.d("pl2303 LiveType", value.toString())
        LogUtils.d("pl2303 LiveType", value.toString())
        when (value.type) {
            LiveType.DA_JIE -> {
                view.warning()

                val rotate = rotate()
                LogToFile.w("DA_JIE", "打劫:${view.board.toShortString(rotate)}\t:\t${pl2303interface?.reverStr(cmdData)}")
            }
            LiveType.LAST_BACK -> {
//                data.tileViewHasChanged(GameStep(1))
//                view.tileViewLastBack()
                view.warning()
                MyService.TILE_ERROR = true
                errorListener.onErrorList(value.errorList)
            }
            LiveType.LAST_ERROR -> {
                view.warning()
                MyService.TILE_ERROR = true
                errorListener.onError(value.chessChange)
                val change = value.chessChange
                view.tileViewError("位置（" + (change.x + 64).toChar() + "，" + change.y + "）发生异常")

                val rotate = rotate()
                LogToFile.w("LAST_ERROR", "错误$change:${view.board.toShortString(rotate)}\t:\t${pl2303interface?.reverStr(cmdData)}")
            }
            LiveType.LITTLE_ERROR -> {
                view.warning()

                val rotate = rotate()
                LogToFile.w("LITTLE_ERROR", "错误:${view.board.toShortString(rotate)}\t:\t${pl2303interface?.reverStr(cmdData)}")
            }
            LiveType.LAST_ERROR_MORE, LiveType.LAST_ERROR_MORE_ADD -> {
                view.warning()
                MyService.TILE_ERROR = true
                errorListener.onErrorList(value.errorList)
                val sb = StringBuilder()
                for (c in value.errorList) {
                    sb.append("(").append((c.x + 64).toChar()).append(",").append(c.y).append(")")
                }
                view.tileViewError("位置" + sb + "发生异常")

                val rotate = rotate()
                LogToFile.w("MORE_ERROR", "错误$sb:${view.board.toShortString(rotate)}\t:\t${pl2303interface?.reverStr(cmdData)}")
            }
            LiveType.FINISH_PICK -> {
//                view.tileViewFinshPick()
                MyService.TILE_ERROR = false
                errorListener.onSuccess()
            }
            LiveType.DO_NOTHING -> {
                MyService.TILE_ERROR = false
                errorListener.onSuccess()
            }
            LiveType.NORMAL -> {
//                putChess(value.allStep)
                if (MyService.TILE_ERROR) {
                    MyService.TILE_ERROR = false
                    errorListener.onSuccess()
                    Thread.sleep(500)
                }

                if ((game.bw == 1 && value.allStep.startsWith("-"))
                        || (game.bw == 2 && value.allStep.startsWith("+")))
                    return
                //点击屏幕落子
                val index = value.index//返回的数据棋盘白方左手边为1，白方右手边为19，即棋盘的左下角为1，横向右下角为19
                var x: Int
                var y: Int
                if (ScreenUtil.isPortrait(pl2303interface?.mcontext)) {//竖屏
                    //转换为棋盘右下角为1，横向左下角为19  棋盘返回1时对应点屏幕棋盘左下角
                    x = Board.n - index / Board.n - if (index % Board.n == 0) 0 else 1//0-18
                    y = if (index % Board.n == 0) Board.n - 1 else index % Board.n - 1 //0-18
                } else {//横屏
                    //转换为棋盘右上角为1，竖向右下角为19  棋盘返回1时对应点屏幕棋盘左上角
                    x = if (index % Board.n == 0) Board.n - 1 else index % Board.n - 1 //0-18
                    y = index / Board.n - if (index % Board.n == 0) 1 else 0 //0-18

                    //棋盘返回1时对应点屏幕棋盘右下角, 7.1系统对比5.1横屏旋转了180
                    if (Build.VERSION.SDK_INT >= 24) {
                        x = Board.n - 1 - x

                        y = Board.n - 1 - y
                    }
                }


                val size = rectF.width() / Board.n

                val xLocation = rectF.left + size * (y + 0.5f)
                val yLocation = rectF.top + size * (x + 0.5f)
                println("点击屏幕落子:index" + index + ";x" + (x + 1) + "/" + xLocation + ";y" + (y + 1) + "/" + yLocation)
                click(xLocation, yLocation)
                if (ServiceActivity.PLATFORM == PLATFORM_XB) {//新博需要双击
                    clickN(xLocation, yLocation,3)
                }
                if (ServiceActivity.PLATFORM == PLATFORM_YK) {
                    clickN(240f, 735f,3)
                }

                if (ServiceActivity.PLATFORM == PLATFORM_YC) {
                    clickN(240f, 569f,1)
                }
                if (ServiceActivity.PLATFORM == PLATFORM_JJ) {
                    clickN(480f, 367f,3)
                }
            }
            LiveType.GO_BACK -> {
//                val backNum = value.backNum
//                data.tileViewHasChanged(GameStep(backNum))
//                view.tileViewGoBack(backNum)

                view.warning()
                MyService.TILE_ERROR = true
                errorListener.onErrorList(value.errorList)
            }
            LiveType.BACK_NEW -> {
//                val goBackNum = value.backNum
//                val newStep = value.backNew
//                data.tileViewHasChanged(GameStep(goBackNum))
//                view.tileViewBackNew(goBackNum, newStep)

                view.warning()
                MyService.TILE_ERROR = true
                errorListener.onErrorList(value.errorList)
            }
            LiveType.NEW_CHESS_2 -> {
//                putChess(value.allStep)

                view.warning()
                MyService.TILE_ERROR = true
                errorListener.onErrorList(value.errorList)
            }
        }
    }

    private fun clickN(x: Float, y: Float, n: Int) {
        if (n > 0) {
            Thread.sleep(500)

            click(x, y)

            clickN(x, y, n - 1)
        }
    }

    private fun rotate(): Int {
        return when {
            ScreenUtil.isPortrait(pl2303interface?.mcontext) -> 270
            Build.VERSION.SDK_INT >= 24 -> 180
            else -> 0
        }
    }

    fun putChess(step: String) {
        view.tileViewNormal(step)
    }

    fun lamb(singleGoCoodinate: String,
             isbremove: Boolean, rotate: Int) {
        pl2303interface?.WritesingleGoCoodinate(singleGoCoodinate, isbremove, rotate)
    }

    //是否是正常的棋谱，黑白交替的
    fun isNormalChess(): Boolean {
        val sgf = sgf()
        val compile = Pattern.compile("(\\+[0-9]{4}-[0-9]{4})+(?:\\+[0-9]{4})?")
        val matcher = compile.matcher(sgf)
        if (matcher.find()) {
            return matcher.group(0).length == sgf.length && isNormalChess
        }
        return false
    }

    fun sgf() = SgfHelper.getBoardsgfStr(view.board)

    private val processBuilder = ProcessBuilder()
    private fun click(x: Float, y: Float) {
        println("点击：x：$x , y：$y")
        if (Build.VERSION.SDK_INT < 24) {
            val order = arrayOf("input", "tap", "" + x, "" + y)
            try {
                processBuilder.command(*order).start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            RxBus.getDefault().send(RxEvent(RxEvent.click, "$x,$y"))
        }
    }

    var rectF = RectF()
    private var disposable: Disposable? = null

    fun connect(view: View?) {
        disposable = io.reactivex.Observable.timer(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { pl2303interface?.callResume(view) }
                .observeOn(Schedulers.io())
                .flatMap { io.reactivex.Observable.timer(300, TimeUnit.MILLISECONDS) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { pl2303interface?.OpenUARTDevice(view, Board.n) }
    }

    private var isNormalChess = true
    fun updateBoard(a: Array<IntArray>) {
        view.board.currentGrid.a = a
        isNormalChess = false
    }

    fun updateCurBW(bw: Int) {
        view.board.setCurBW(if (bw == 1) 1 else 2)
    }

    fun isConnected() = pl2303interface?.PL2303MultiLiblinkExist() ?: false

    fun disConnect() {
        pl2303interface?.WriteToUARTDevice("~RGC#")
        pl2303interface?.pl2303DisConnect()
        disposable?.dispose()
    }

    fun onDestroy() {
        pl2303interface?.WriteToUARTDevice("~CAL#")
        pl2303interface?.WriteToUARTDevice("~CTS1#")
        pl2303interface?.WriteToUARTDevice("~BOD19#")
        pl2303interface?.WriteToUARTDevice("~RGC#")
        pl2303interface?.callDestroy()

        disposable?.dispose()
    }
}