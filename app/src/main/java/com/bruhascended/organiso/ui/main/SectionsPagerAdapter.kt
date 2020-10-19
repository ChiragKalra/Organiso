package com.bruhascended.organiso.ui.main

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class CategoryPagerAdapter (
    private val vc: Array<Int>,
    fm: FragmentManager,
    lc: Lifecycle
): FragmentStateAdapter(fm, lc) {

    override fun createFragment(position: Int) =
        CategoryFragment.newInstance(vc[position], position)

    override fun getItemCount() = vc.size
}