package dev.frostguard.tasks.dailies;

import dev.frostguard.api.configs.ConfigurationKeyEnum;
import dev.frostguard.api.configs.TemplatesEnum;
import dev.frostguard.api.configs.TpDailyTaskEnum;
import dev.frostguard.api.domain.AccountDescriptor;
import dev.frostguard.api.domain.ImageSearchResultData;
import dev.frostguard.api.domain.PointData;
import dev.frostguard.api.domain.AreaData;
import dev.frostguard.api.domain.OcrSettingsData;
import dev.frostguard.engine.nav.SearchConfigConstants;
import dev.frostguard.engine.helper.TemplateSearchHelper.SearchConfig;
import dev.frostguard.engine.helper.RecognitionConsensus;
import dev.frostguard.engine.helper.GreenLabelActionDetector;
import dev.frostguard.vision.convert.ImageConverter;
import dev.frostguard.engine.schedule.DelayedTask;
import dev.frostguard.engine.schedule.LaunchPoint;
import dev.frostguard.vision.convert.GameTimeUtils;
import java.time.Duration;
import java.time.LocalDateTime;

public class VipRoutine extends DelayedTask {

private static final boolean DEFAULT_BUY_MONTHLY_VIP_VALUE = false;

private static final PointData VIP_MENU_BUTTON_TOP_LEFT_VALUE = new PointData(430, 48);

private static final PointData VIP_MENU_BUTTON_BOTTOM_RIGHT_VALUE = new PointData(530, 85);

private static final PointData PURCHASE_CONFIRM_TOP_LEFT_VALUE = new PointData(520, 810);

private static final PointData PURCHASE_CONFIRM_BOTTOM_RIGHT_VALUE = new PointData(650, 850);

private static final PointData PURCHASE_FINAL_CONFIRM_TOP_LEFT_VALUE = new PointData(250, 770);

private static final PointData PURCHASE_FINAL_CONFIRM_BOTTOM_RIGHT_VALUE = new PointData(480, 800);

private static final PointData VIP_EXPIRATION_TIME_TOP_LEFT_MS = new PointData(273, 1170);

private static final PointData VIP_EXPIRATION_TIME_BOTTOM_RIGHT_MS = new PointData(461, 1213);

private static final PointData DAILY_CHEST_REWARDS_TOP_LEFT_VALUE = new PointData(540, 813);

private static final PointData DAILY_CHEST_REWARDS_BOTTOM_RIGHT_VALUE = new PointData(624, 835);

private static final PointData VIP_POINT_REWARDS_TOP_LEFT_VALUE = new PointData(602, 263);

private static final PointData VIP_POINT_REWARDS_BOTTOM_RIGHT_VALUE = new PointData(650, 293);

private boolean buyMonthlyVip;

private LocalDateTime nextMonthlyVipBuyTime;

public VipRoutine(AccountDescriptor profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

@Override
	protected void execute() {
		hydrateConfiguration();

		if (!openUpVipMenu()) {
			logWarning(routineLogVipLine("Could not open VIP menu."));
			queueNextExecution();
			return;
		}

        // Daily rewards automation must never initiate a VIP purchase,
        // regardless of the stored monthly-purchase setting or UI version.
        if (buyMonthlyVip) {
            logWarning(routineLogVipLine("Monthly VIP purchase disabled for automated daily rewards; no spending permitted"));
        }

		redeemDailyChestRewards();

		redeemVipPointRewards();

		dismissVipMenu();

		queueNextExecution();

	}

@Override
	protected LaunchPoint getRequiredStartLocation() {
		return LaunchPoint.ANY;
	}

private void scanAndStoreVipExpirationTime() {
		logDebug(routineLogVipLine("Reading VIP expiration time from screen"));

		OcrSettingsData timeSettings = OcrSettingsData.assembler()
				.textLayout(OcrSettingsData.TextLayout.SINGLE_LINE)

				.charWhitelist("0123456789d")
				.build();

		Duration expirationTime = durationHelper.attemptRecognition(
				VIP_EXPIRATION_TIME_TOP_LEFT_MS,
				VIP_EXPIRATION_TIME_BOTTOM_RIGHT_MS,
				3,
				200L,
				timeSettings,
				GameTimeUtils::isAcceptedFormat,
				GameTimeUtils::parseDuration);

		if (expirationTime == null) {
			logWarning(routineLogVipLine("Could not read VIP expiration time from screen"));
			return;
		}

		LocalDateTime calculatedExpirationTime = LocalDateTime.now().plus(expirationTime);
		logDebug(routineLogVipLine("OCR result: '" + GameTimeUtils.formatCountdown(calculatedExpirationTime) + "'"));
		nextMonthlyVipBuyTime = calculatedExpirationTime;


		writeProfileSetting(
				ConfigurationKeyEnum.VIP_NEXT_MONTHLY_BUY_TIME_STRING,
				calculatedExpirationTime.toString());

		logInfo(routineLogVipLine(String.format("VIP expiration time stored: %s (expires in %s)",
				calculatedExpirationTime.format(DATETIME_FORMATTER),
				GameTimeUtils.formatCountdown(calculatedExpirationTime))));

	}

private void manageMonthlyVipPurchase() {
		logDebug(routineLogVipLine("Handling monthly VIP purchase check"));


		if (nextMonthlyVipBuyTime != null && LocalDateTime.now().isBefore(nextMonthlyVipBuyTime)) {
			logInfo(routineLogVipLine(String.format("Monthly VIP purchase on cooldown. Next purchase available at: %s (in %s)",
					nextMonthlyVipBuyTime.format(DATETIME_FORMATTER),
					GameTimeUtils.formatCountdown(nextMonthlyVipBuyTime))));
			return;
		}

		logDebug(routineLogVipLine("Cooldown expired or not set. Inspecting VIP status."));


		ImageSearchResultData unlockButton = templateSearchHelper.locatePattern(
				TemplatesEnum.VIP_UNLOCK_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (unlockButton.isFound()) {


			logInfo(routineLogVipLine("Monthly VIP is not active. Initiating purchase."));
			purchaseMonthlyVipFlow(unlockButton.getPoint());
		} else {


			logInfo(routineLogVipLine("Monthly VIP is already active."));
		}


		scanAndStoreVipExpirationTime();
	}

private void redeemDailyChestRewards() {
        if (isCurrentVipMenuVisible()) {
            var locked = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_VIP_LOCKED,
                    SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                            .withArea(AreaData.of(515, 760, 675, 886)).build());
            if (locked.isFound()) {
                logInfo(routineLogVipLine("Daily VIP Free Bundle is locked; skipping without opening a paid offer"));
                return;
            }
            ImageSearchResultData freeClaim = verifiedFreeVipClaim(
                    new GreenLabelActionDetector.Region(505, 755, 688, 882));
            if (!freeClaim.isFound()) {
                logInfo(routineLogVipLine("VIP Daily Bundle has no verified free Claim button; skipping"));
                return;
            }
            tapInside(freeClaim);
            sleepTask(350);
            logInfo(routineLogVipLine("Verified VIP free Claim pressed once"));
            return;
        }
        // Retain the old supported UI only if its exact VIP screen is visible.
        if (templateSearchHelper.locatePattern(TemplatesEnum.VIP_MENU,
                SearchConfigConstants.DEFAULT_SINGLE).isFound()) {
            tapInside(DAILY_CHEST_REWARDS_TOP_LEFT_VALUE, DAILY_CHEST_REWARDS_BOTTOM_RIGHT_VALUE, 1, 300);
        }
    }

    private ImageSearchResultData verifiedFreeVipClaim(GreenLabelActionDetector.Region region) {
        var first = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), region);
        if (!first.isFound()) return ImageSearchResultData.miss();
        sleepTask(160);
        var second = GreenLabelActionDetector.detect(
                ImageConverter.toBufferedImage(emuManager.captureScreen(EMULATOR_NUMBER)), region);
        return RecognitionConsensus.agrees(first, second, 12) ? second : ImageSearchResultData.miss();
    }

private void purchaseMonthlyVipFlow(PointData unlockButtonPoint) {


		tapNear(unlockButtonPoint);
		sleepTask(1000);


		logDebug(routineLogVipLine("Confirming VIP purchase (step 1/2)"));
		tapInside(PURCHASE_CONFIRM_TOP_LEFT_VALUE, PURCHASE_CONFIRM_BOTTOM_RIGHT_VALUE);
		sleepTask(500);


		logDebug(routineLogVipLine("Confirming VIP purchase (step 2/2)"));
		tapInside(PURCHASE_FINAL_CONFIRM_TOP_LEFT_VALUE, PURCHASE_FINAL_CONFIRM_BOTTOM_RIGHT_VALUE);
		sleepTask(500);


		pressBack();
		sleepTask(500);


		logInfo(routineLogVipLine("Monthly VIP purchase completed."));
	}

private void redeemVipPointRewards() {
        if (isCurrentVipMenuVisible()) {
            ImageSearchResultData calendar = templateSearchHelper.locateAction(
                    TemplatesEnum.SERVE_EMU_VIP_CALENDAR,
                    SearchConfig.builder().withMaxAttempts(2).withDelay(150).withThreshold(90)
                            .withArea(AreaData.of(568, 285, 690, 403)).build());
            if (!calendar.isFound()) {
                logInfo(routineLogVipLine("VIP sign-in calendar not confirmed; no blind reward tap"));
                return;
            }
            tapInside(calendar);
            sleepTask(400);
            boolean signIn = templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_VIP_SIGNIN,
                    SearchConfig.builder().withMaxAttempts(2).withDelay(150).withThreshold(90)
                            .withArea(AreaData.of(40, 265, 510, 385)).build()).isFound();
            if (!signIn) {
                logWarning(routineLogVipLine("Calendar tapped but Sign-in Streak screen unverified"));
                return;
            }
            // Only the free, green-labelled Claim action is eligible; the
            // "tomorrow" banner and shop buttons are never pressed blindly.
            ImageSearchResultData free = verifiedFreeVipClaim(
                    new GreenLabelActionDetector.Region(180, 985, 545, 1148));
            if (free.isFound()) {
                tapInside(free);
                sleepTask(300);
                logInfo(routineLogVipLine("Sign-in Streak free claim tapped once"));
            } else {
                logInfo(routineLogVipLine("Sign-in Streak has no verified free claim today"));
            }
            if (templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_VIP_SIGNIN,
                    SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                            .withArea(AreaData.of(40, 265, 510, 385)).build()).isFound()) {
                tapInside(new PointData(607, 298), new PointData(659, 360));
                sleepTask(250);
            }
            return;
        }
        // Old VIP UI support remains in place; never press the modern shopping
        // cart location as though it were a free VIP point reward.
        if (templateSearchHelper.locatePattern(TemplatesEnum.VIP_MENU,
                SearchConfigConstants.DEFAULT_SINGLE).isFound())
            tapInside(VIP_POINT_REWARDS_TOP_LEFT_VALUE, VIP_POINT_REWARDS_BOTTOM_RIGHT_VALUE, 1, 300);
    }

private void hydrateConfiguration() {
		Boolean configuredBuyVip = profile.getConfig(
				ConfigurationKeyEnum.VIP_MONTHLY_BUY_BOOL, Boolean.class);
		this.buyMonthlyVip = (configuredBuyVip != null) ? configuredBuyVip : DEFAULT_BUY_MONTHLY_VIP_VALUE;


		String nextBuyTimeStr = profile.getConfig(
				ConfigurationKeyEnum.VIP_NEXT_MONTHLY_BUY_TIME_STRING, String.class);

		if (nextBuyTimeStr != null && !nextBuyTimeStr.isEmpty()) {
			try {
				this.nextMonthlyVipBuyTime = LocalDateTime.parse(nextBuyTimeStr);
			} catch (Exception e) {
				logWarning(routineLogVipLine("Could not parse stored next monthly VIP buy time: " + e.getMessage()));
				this.nextMonthlyVipBuyTime = null;
			}
		} else {
			this.nextMonthlyVipBuyTime = null;
		}

		logDebug(routineLogVipLine(String.format("Configuration loaded - Buy monthly VIP: %s, Next buy: %s",
				buyMonthlyVip,
				(nextMonthlyVipBuyTime != null)
						? nextMonthlyVipBuyTime.format(DATETIME_FORMATTER)
						: "not set")));
	}

private void queueNextExecution() {
		LocalDateTime nextGameReset = GameTimeUtils.dailyResetTime();
		LocalDateTime nextExecutionTime = nextGameReset;

		if (buyMonthlyVip && nextMonthlyVipBuyTime != null) {


			if (nextMonthlyVipBuyTime.isBefore(nextGameReset)) {
				nextExecutionTime = nextMonthlyVipBuyTime;
				logInfo(routineLogVipLine("Next execution scheduled for monthly VIP buy at: " +
						nextMonthlyVipBuyTime.format(DATETIME_FORMATTER)));
			} else {
				logInfo(routineLogVipLine("Next execution scheduled for game reset at: " +
						nextGameReset.format(DATETIME_FORMATTER)));
			}
		} else {
			logInfo(routineLogVipLine("Next execution scheduled for game reset at: " +
					nextGameReset.format(DATETIME_FORMATTER)));
		}

		reschedule(nextExecutionTime);
	}

private String routineLogVipLine(String note) {
        return "VipRoutine | " + note;
    }

private boolean isCurrentVipMenuVisible() {
        return templateSearchHelper.locatePattern(TemplatesEnum.SERVE_EMU_VIP_TITLE,
                SearchConfig.builder().withMaxAttempts(1).withThreshold(90)
                        .withArea(AreaData.of(78, 70, 195, 150)).build()).isFound();
    }

private void dismissVipMenu() {
        if (isCurrentVipMenuVisible()) {
            tapInside(new PointData(12, 75), new PointData(73, 139));
            sleepTask(250);
            return;
        }
        if (templateSearchHelper.locatePattern(TemplatesEnum.VIP_MENU,
                SearchConfigConstants.DEFAULT_SINGLE).isFound()) {
            pressBack();
            sleepTask(350);
        }
    }

private boolean openUpVipMenu() {
        if (isCurrentVipMenuVisible()) return true;
        var hud = templateSearchHelper.locateAction(TemplatesEnum.SERVE_EMU_VIP_HUD,
                SearchConfig.builder().withMaxAttempts(2).withDelay(120)
                        .withThreshold(90).withArea(AreaData.of(426, 105, 544, 170)).build());
        if (hud.isFound()) {
            tapInside(hud);
            sleepTask(300);
            return isCurrentVipMenuVisible();
        }
        // Classic BlueStacks navigation: only accept its existing, verified
        // VIP template as a postcondition.
        tapInside(VIP_MENU_BUTTON_TOP_LEFT_VALUE, VIP_MENU_BUTTON_BOTTOM_RIGHT_VALUE);
        sleepTask(500);
        return templateSearchHelper.locatePattern(TemplatesEnum.VIP_MENU,
                SearchConfigConstants.DEFAULT_SINGLE).isFound();
    }

}
