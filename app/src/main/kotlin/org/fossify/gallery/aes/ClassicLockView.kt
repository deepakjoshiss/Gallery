package org.fossify.gallery.aes

import android.content.Context
import android.graphics.PorterDuff
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.gallery.R

/* loaded from: classes.dex */
class ClassicLockView(context: Context?, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet), View.OnClickListener, TextWatcher {
    var mBtn0: View? = null
    var mBtn1: View? = null
    var mBtn2: View? = null
    var mBtn3: View? = null
    var mBtn4: View? = null
    var mBtn5: View? = null
    var mBtn6: View? = null
    var mBtn7: View? = null
    var mBtn8: View? = null
    var mBtn9: View? = null
    var mBtns: ArrayList<View?> = ArrayList()
    var mDividerInput: View? = null
    var mEPass: EditText? = null
    var mInput: String = ""
    var mIsSetup: Boolean = false
    var mIvBackSpace: ImageView? = null
    var mOldInput: String = ""
    var mPasswordLevel: Int = 0
    var mTv0: TextView? = null
    var mTv1: TextView? = null
    var mTv2: TextView? = null
    var mTv3: TextView? = null
    var mTv4: TextView? = null
    var mTv5: TextView? = null
    var mTv6: TextView? = null
    var mTv7: TextView? = null
    var mTv8: TextView? = null
    var mTv9: TextView? = null
    var mTvChars: ArrayList<TextView> = ArrayList()
    var mTvNumbers: ArrayList<TextView?> = ArrayList()
    var mGo: ImageView? = null
    private var mLabel: TextView? = null

    private var mPassCallback: TextSubmitCallback? = null

    // android.text.TextWatcher
    override fun afterTextChanged(editable: Editable) {
    }

    // android.text.TextWatcher
    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
    }

    fun initData() {
    }

    // android.view.View
    public override fun onFinishInflate() {
        super.onFinishInflate()
        initData()
        this.mBtn0 = findViewById(R.id.button0)
        this.mBtn1 = findViewById(R.id.button1)
        this.mBtn2 = findViewById(R.id.button2)
        this.mBtn3 = findViewById(R.id.button3)
        this.mBtn4 = findViewById(R.id.button4)
        this.mBtn5 = findViewById(R.id.button5)
        this.mBtn6 = findViewById(R.id.button6)
        this.mBtn7 = findViewById(R.id.button7)
        this.mBtn8 = findViewById(R.id.button8)
        this.mBtn9 = findViewById(R.id.button9)
        this.mTv0 = findViewById<View>(R.id.tv0) as TextView
        this.mTv1 = findViewById<View>(R.id.tv1) as TextView
        this.mTv2 = findViewById<View>(R.id.tv2) as TextView
        this.mTv3 = findViewById<View>(R.id.tv3) as TextView
        this.mTv4 = findViewById<View>(R.id.tv4) as TextView
        this.mTv5 = findViewById<View>(R.id.tv5) as TextView
        this.mTv6 = findViewById<View>(R.id.tv6) as TextView
        this.mTv7 = findViewById<View>(R.id.tv7) as TextView
        this.mTv8 = findViewById<View>(R.id.tv8) as TextView
        this.mTv9 = findViewById<View>(R.id.tv9) as TextView
        mBtns.add(this.mBtn0)
        mBtns.add(this.mBtn1)
        mBtns.add(this.mBtn2)
        mBtns.add(this.mBtn3)
        mBtns.add(this.mBtn4)
        mBtns.add(this.mBtn5)
        mBtns.add(this.mBtn6)
        mBtns.add(this.mBtn7)
        mBtns.add(this.mBtn8)
        mBtns.add(this.mBtn9)
        mTvNumbers.add(this.mTv0)
        mTvNumbers.add(this.mTv1)
        mTvNumbers.add(this.mTv2)
        mTvNumbers.add(this.mTv3)
        mTvNumbers.add(this.mTv4)
        mTvNumbers.add(this.mTv5)
        mTvNumbers.add(this.mTv6)
        mTvNumbers.add(this.mTv7)
        mTvNumbers.add(this.mTv8)
        mTvNumbers.add(this.mTv9)
        mTvChars.add(findViewById<View>(R.id.tvChar0) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar1) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar2) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar3) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar4) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar5) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar6) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar7) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar8) as TextView)
        mTvChars.add(findViewById<View>(R.id.tvChar9) as TextView)

        mLabel = findViewById(R.id.tvLabel)
        mIvBackSpace = findViewById(R.id.ivBackSpace)
        mIvBackSpace?.setOnClickListener(this)

        mGo = findViewById(R.id.buttonOK)
        mGo?.setOnClickListener(this)

        mEPass = findViewById(R.id.ePass)
        mEPass?.addTextChangedListener(this)

        for (i in mBtns.indices) {
            mBtns[i]!!.setOnClickListener(this)
        }
    }

    // android.view.View.OnClickListener
    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.ivBackSpace) {
            if (mInput.isNotEmpty()) {
                val substring = mInput.substring(0, mInput.length - 1)
                this.mInput = substring
                mEPass!!.setText(substring)
                mEPass!!.setSelection(mInput.length)
                return
            }
            return
        }
        when (id) {
            R.id.button0 -> {
                val str2 = this.mInput + mTv0!!.text.toString()
                this.mInput = str2
                mEPass!!.setText(str2)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button1 -> {
                val str3 = this.mInput + mTv1!!.text.toString()
                this.mInput = str3
                mEPass!!.setText(str3)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button2 -> {
                val str4 = this.mInput + mTv2!!.text.toString()
                this.mInput = str4
                mEPass!!.setText(str4)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button3 -> {
                val str5 = this.mInput + mTv3!!.text.toString()
                this.mInput = str5
                mEPass!!.setText(str5)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button4 -> {
                val str6 = this.mInput + mTv4!!.text.toString()
                this.mInput = str6
                mEPass!!.setText(str6)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button5 -> {
                val str7 = this.mInput + mTv5!!.text.toString()
                this.mInput = str7
                mEPass!!.setText(str7)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button6 -> {
                val str8 = this.mInput + mTv6!!.text.toString()
                this.mInput = str8
                mEPass!!.setText(str8)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button7 -> {
                val str9 = this.mInput + mTv7!!.text.toString()
                this.mInput = str9
                mEPass!!.setText(str9)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button8 -> {
                val str10 = this.mInput + mTv8!!.text.toString()
                this.mInput = str10
                mEPass!!.setText(str10)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.button9 -> {
                val str11 = this.mInput + mTv9!!.text.toString()
                this.mInput = str11
                mEPass!!.setText(str11)
                mEPass!!.setSelection(mInput.length)
                return
            }

            R.id.buttonOK -> {
                mPassCallback!!.onSubmit(mEPass!!.text.toString(), "")
                return
            }

            else -> return
        }
    }

    // android.text.TextWatcher
    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
        mPassCallback!!.onTextChange(mEPass!!.text.toString(), "")
    }

    fun resetInput() {
        mEPass!!.setText("")
        this.mInput = ""
    }

    fun setPassCallback(callback: TextSubmitCallback?) {
        this.mPassCallback = callback
    }

    fun setLabel(label: String?) {
        mLabel!!.text = label
    }

    fun setTextColor(i: Int) {
        for (i2 in mBtns.indices) {
            mTvNumbers[i2]!!.setTextColor(i)
            mTvChars[i2].setTextColor(i)
        }
        mIvBackSpace!!.setColorFilter(i, PorterDuff.Mode.SRC_IN)
        mEPass!!.setTextColor(i)
        mDividerInput!!.setBackgroundColor(i)
    }
}
