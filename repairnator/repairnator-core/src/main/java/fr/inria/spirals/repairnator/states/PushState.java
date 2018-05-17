package fr.inria.spirals.repairnator.states;

/**
 * Created by urli on 27/04/2017.
 */
public enum PushState {
    NONE,
    REPO_INITIALIZED, REPO_NOT_INITIALIZED,
    REPAIR_INFO_COMMITTED, REPAIR_INFO_NOT_COMMITTED,
    PATCH_COMMITTED, PATCH_NOT_COMMITTED,
    REPO_PUSHED, REPO_NOT_PUSHED,
    END_PUSHED
}
