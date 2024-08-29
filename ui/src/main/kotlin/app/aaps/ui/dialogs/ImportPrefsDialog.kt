package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventImportPrefsStatus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.database.entities.UserEntry
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogImportPrefsBinding

import dagger.android.support.DaggerDialogFragment


import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.system.exitProcess


class ImportPrefsDialog : DaggerDialogFragment() {

    @Inject
    lateinit var rxBus: RxBus

    @Inject
    lateinit var aapsLogger: AAPSLogger

    @Inject
    lateinit var rh: ResourceHelper

    @Inject
    lateinit var fabricPrivacy: FabricPrivacy

    @Inject
    lateinit var aapsSchedulers: AapsSchedulers

    @Inject
    lateinit var sp: SP
    @Inject
    lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    private var state: String? = null
    private var percent = 0

    private var _binding: DialogImportPrefsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = false

        state = savedInstanceState?.getString("state", null)
        percent = savedInstanceState?.getInt("percent", 0) ?: 0

        _binding = DialogImportPrefsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(rh.gs(R.string.preferences_auto_importing_title))
        val defaultMessage = rh.gs(R.string.preferences_importing)
//        binding.stop.setOnClickListener { dismiss() }
        binding.stop.setOnClickListener {
            //onPriming()
            dismiss()
//            activity?.let {activity-> restartAppAfterImport(activity) }
        }
        binding.status.text = state ?: defaultMessage
        binding.progressbar.max = 100
        binding.progressbar.progress = percent
        binding.stop.text = rh.gs(R.string.close)
        binding.title.text = rh.gs(R.string.preferences_auto_importing_title)
    }


    private fun restartAppAfterImport(context: Context) {
//        rxBus.send(EventDiaconnG8PumpLogReset())
//         sp.putBoolean(R.string.key_setupwizard_processed, true)
        OKDialog.show(context, rh.gs(R.string.setting_imported), rh.gs(R.string.restartingapp)) {
            uel.log(UserEntry.Action.IMPORT_SETTINGS, UserEntry.Sources.Maintenance)
            aapsLogger.debug(LTag.CORE, "Exiting")
            rxBus.send(EventAppExit())
            if (context is AppCompatActivity) {
                context.finish()
            }
            System.runFinalization()
            exitProcess(0)
        }
    }

    override fun onResume() {
        super.onResume()
        aapsLogger.debug(LTag.UI, "onResume")
        val unit = if (percent == 100) {
            dismiss()
            return
        } else
            dialog?.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        disposable += rxBus
            .toObservable(EventImportPrefsStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event: EventImportPrefsStatus ->
                if (_binding != null) {
                    aapsLogger.debug(
                        LTag.UI,
                        "Status: " + event.status + " Percent: " + event.percent + "Result: " + event.result
                    )
                    binding.status.text = buildString {
                        append(event.status)
                        append(" ${event.percent}%")
                    }
                    binding.progressbar.progress = event.percent
                    if (event.percent >= 100) {
                        SystemClock.sleep(100)
                        dismiss()
                        activity?.let {activity-> restartAppAfterImport(activity) }
                    }
                    if(event.result==-1){
                        SystemClock.sleep(100)
                        binding.stop.visibility=View.VISIBLE
//                        dismiss()
                    }
                    state = event.status
                    percent = event.percent
                }
            }, fabricPrivacy::logException)
    }

    override fun onPause() {
        aapsLogger.debug(LTag.UI, "onPause")
        super.onPause()
        disposable.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("state", state)
        outState.putInt("percent", percent)
        super.onSaveInstanceState(outState)
    }
}