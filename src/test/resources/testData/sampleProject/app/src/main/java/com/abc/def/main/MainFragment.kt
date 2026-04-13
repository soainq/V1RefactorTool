package com.abc.def.main

import androidx.fragment.app.Fragment

class MainFragment : Fragment(R.layout.activity_main) {
    fun showMainPadding(): Int = resources.getDimensionPixelSize(R.dimen.main_content_padding)
}
