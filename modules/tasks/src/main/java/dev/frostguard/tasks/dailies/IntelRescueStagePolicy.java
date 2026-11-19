package dev.frostguard.tasks.dailies;

/** Rescue can transition directly into the World or via Squad Settings.
 * A still-visible Rescue button is not enough to infer either success or
 * failure; a verified Deploy on the correct formation ROI is a new state. */
final class IntelRescueStagePolicy {
    enum Decision { DIRECT_ACKNOWLEDGED, VERIFY_DEPLOY, DEFER_UNKNOWN }
    private IntelRescueStagePolicy(){}
    static Decision decide(boolean rescueDisappeared,boolean deployVerified){
        if(deployVerified)return Decision.VERIFY_DEPLOY;
        return rescueDisappeared?Decision.DIRECT_ACKNOWLEDGED:Decision.DEFER_UNKNOWN;
    }
}
