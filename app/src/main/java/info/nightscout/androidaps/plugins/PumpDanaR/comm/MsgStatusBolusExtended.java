package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.TempExBasal;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;

public class MsgStatusBolusExtended extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusBolusExtended.class);

    public MsgStatusBolusExtended() {
        SetCommand(0x0207);
    }

    public void handleMessage(byte[] bytes) {
        boolean isExtendedInProgress = intFromBuff(bytes, 0, 1) == 1;
        int extendedBolusHalfHours = intFromBuff(bytes, 1, 1);
        int extendedBolusMinutes = extendedBolusHalfHours * 30;

        double extendedBolusAmount = intFromBuff(bytes, 2, 2) / 100d;
        int extendedBolusSoFarInSecs = intFromBuff(bytes, 4, 3);
// This is available only on korean, but not needed now
//        int extendedBolusDeliveryPulse = intFromBuff(bytes, 7, 2);
//        int isEasyUIUserSleep = intFromBuff(bytes, 9, 1);

        int extendedBolusSoFarInMinutes = extendedBolusSoFarInSecs / 60;
        double extendedBolusAbsoluteRate = isExtendedInProgress ? extendedBolusAmount / extendedBolusMinutes * 60 : 0d;
        Date extendedBolusStart = isExtendedInProgress ? getDateFromSecAgo(extendedBolusSoFarInSecs) : new Date(0);
        int extendedBolusRemainingMinutes = extendedBolusMinutes - extendedBolusSoFarInMinutes;

        DanaRPump pump = DanaRPump.getInstance();
        pump.isExtendedInProgress = isExtendedInProgress;
        pump.extendedBolusMinutes = extendedBolusMinutes;
        pump.extendedBolusAmount = extendedBolusAmount;
        pump.extendedBolusSoFarInMinutes = extendedBolusSoFarInMinutes;
        pump.extendedBolusAbsoluteRate = extendedBolusAbsoluteRate;
        pump.extendedBolusStart = extendedBolusStart;
        pump.extendedBolusRemainingMinutes = extendedBolusRemainingMinutes;

        updateExtendedBolusInDB();

        if (Config.logDanaMessageDetail) {
            log.debug("Is extended bolus running: " + isExtendedInProgress);
            log.debug("Extended bolus min: " + extendedBolusMinutes);
            log.debug("Extended bolus amount: " + extendedBolusAmount);
            log.debug("Extended bolus so far in minutes: " + extendedBolusSoFarInMinutes);
            log.debug("Extended bolus absolute rate: " + extendedBolusAbsoluteRate);
            log.debug("Extended bolus start: " + extendedBolusStart);
            log.debug("Extended bolus remaining minutes: " + extendedBolusRemainingMinutes);
        }
    }

    @NonNull
    private Date getDateFromSecAgo(int tempBasalAgoSecs) {
        return new Date((long) (Math.ceil(new Date().getTime() / 1000d) - tempBasalAgoSecs) * 1000);
    }

    public static void updateExtendedBolusInDB() {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        DanaRPump pump = DanaRPump.getInstance();
        long now = new Date().getTime();

        if (treatmentsInterface.isExtendedBoluslInProgress()) {
            TempExBasal extendedBolus = treatmentsInterface.getExtendedBolus(new Date().getTime());
            if (pump.isExtendedInProgress) {
                if (extendedBolus.absolute != pump.extendedBolusAbsoluteRate) {
                    // Close current extended
                    treatmentsInterface.extendedBolusStop(now);
                    // Create new
                    TempExBasal newExtended = new TempExBasal();
                    newExtended.timeStart = new Date(now);
                    newExtended.absolute = pump.extendedBolusAbsoluteRate;
                    newExtended.isAbsolute = true;
                    newExtended.duration = pump.extendedBolusMinutes;
                    newExtended.isExtended = true;
                    treatmentsInterface.extendedBolusStart(newExtended);
                }
            } else {
                // Close curent temp basal
                treatmentsInterface.extendedBolusStop(now);
            }
        } else {
            if (pump.isExtendedInProgress) {
                // Create new
                TempExBasal newExtended = new TempExBasal();
                newExtended.timeStart = new Date(now);
                newExtended.absolute = pump.extendedBolusAbsoluteRate;
                newExtended.isAbsolute = true;
                newExtended.duration = pump.extendedBolusMinutes;
                newExtended.isExtended = true;
                treatmentsInterface.extendedBolusStart(newExtended);
            }
        }
    }
}
