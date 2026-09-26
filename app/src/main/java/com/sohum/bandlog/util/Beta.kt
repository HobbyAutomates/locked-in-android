package com.sohum.bandlog.util

/**
 * v2.15 beta switches (the web's BETA_SKIP_ONBOARDING). Everyone is on plan 'beta' for now.
 */
object Beta {
    /**
     * Every onboarding screen shows a quiet "Skip". Signed out it goes to the email sign-up / sign-in
     * screens (the account gets the old default targets); signed in with an incomplete profile it
     * saves nothing and lands on Home, where "Tune your plan" brings the flow back. False: no Skip.
     */
    const val SKIP_ONBOARDING = true
}
