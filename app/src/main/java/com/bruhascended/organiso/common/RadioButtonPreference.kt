package com.bruhascended.organiso.common

import android.content.Context
import android.util.AttributeSet
import androidx.preference.CheckBoxPreference
import com.bruhascended.organiso.R

class RadioButtonPreference : CheckBoxPreference {
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle)
    {
        widgetLayoutResource = R.layout.preference_widget_radiobutton
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        widgetLayoutResource = R.layout.preference_widget_radiobutton
    }

    constructor(context: Context) : this(context, null)

    override fun onClick() {
        if (this.isChecked) { return }
        super.onClick()
    }
}