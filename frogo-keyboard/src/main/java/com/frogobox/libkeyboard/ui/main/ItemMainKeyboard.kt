package com.frogobox.libkeyboard.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.util.Xml
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.EditorInfo.IME_ACTION_NONE
import androidx.annotation.XmlRes
import com.frogobox.libkeyboard.R
import kotlin.math.roundToInt

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard consists of rows of keys.
 * @attr ref android.R.styleable#Keyboard_keyWidth
 * @attr ref android.R.styleable#Keyboard_horizontalGap
 */
class ItemMainKeyboard {
    /** Horizontal gap default for all rows  */
    private var mDefaultHorizontalGap = 0

    /** Default key width  */
    private var mDefaultWidth = 0

    /** Default key height  */
    private var mDefaultHeight = 0

    /** Multiplier for the keyboard height */
    var mKeyboardHeightMultiplier: Float = 1F

    /** Is the keyboard in the shifted state  */
    var mShiftState = SHIFT_OFF

    /** Total height of the keyboard, including the padding and keys  */
    var mHeight = 0

    /** Total width of the keyboard, including left side gaps and keys, but not any gaps on the right side. */
    var mMinWidth = 0

    /** List of keys in this keyboard  */
    var mKeys: MutableList<Key?>? = null

    /** Width of the screen available to fit the keyboard  */
    private var mDisplayWidth = 0

    /** What icon should we show at Enter key  */
    private var mEnterKeyType = IME_ACTION_NONE

    /** Keyboard rows  */
    private val mRows = ArrayList<Row?>()

    companion object {
        private const val TAG_KEYBOARD = "Keyboard"
        private const val TAG_ROW = "Row"
        private const val TAG_KEY = "Key"
        private const val EDGE_LEFT = 0x01
        private const val EDGE_RIGHT = 0x02

        const val KEYCODE_SHIFT = -1
        const val KEYCODE_MODE_CHANGE = -2
        const val KEYCODE_ENTER = -4
        const val KEYCODE_DELETE = -5
        const val KEYCODE_SPACE = 32
        const val KEYCODE_EMOJI = -6

        const val VIBRATE_ON_KEYPRESS = false
        const val SHOW_POPUP_ON_KEYPRESS = true

        const val SHIFT_OFF = 0
        const val SHIFT_ON_ONE_CHAR = 1
        const val SHIFT_ON_PERMANENT = 2

        // limit the count of alternative characters that show up at long pressing a key
        const val MAX_KEYS_PER_MINI_ROW = 9

        // differentiate current and pinned clips at the keyboards' Clipboard section
        const val ITEM_SECTION_LABEL = 0
        const val ITEM_CLIP = 1

        fun getDimensionOrFraction(a: TypedArray, index: Int, base: Int, defValue: Int): Int {
            val value = a.peekValue(index) ?: return defValue
            return when (value.type) {
                TypedValue.TYPE_DIMENSION -> a.getDimensionPixelOffset(index, defValue)
                TypedValue.TYPE_FRACTION -> Math.round(a.getFraction(index,
                    base,
                    base,
                    defValue.toFloat()))
                else -> defValue
            }
        }
    }

    /**
     * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate. Some of the key size defaults can be overridden per row from
     * what the [ItemMainKeyboard] defines.
     * @attr ref android.R.styleable#Keyboard_keyWidth
     * @attr ref android.R.styleable#Keyboard_horizontalGap
     */
    class Row {
        /** Default width of a key in this row.  */
        var defaultWidth = 0

        /** Default height of a key in this row.  */
        var defaultHeight = 0

        /** Default horizontal gap between keys in this row.  */
        var defaultHorizontalGap = 0

        var mKeys = ArrayList<Key>()

        var parent: ItemMainKeyboard

        constructor(parent: ItemMainKeyboard) {
            this.parent = parent
        }

        constructor(res: Resources, parent: ItemMainKeyboard, parser: XmlResourceParser?) {
            this.parent = parent
            val a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.KwKeyboard)
            defaultWidth = getDimensionOrFraction(a,
                R.styleable.KwKeyboard_keyWidth,
                parent.mDisplayWidth,
                parent.mDefaultWidth)
            defaultHeight =
                (res.getDimension(R.dimen.key_height) * this.parent.mKeyboardHeightMultiplier).roundToInt() + 10
            defaultHorizontalGap = getDimensionOrFraction(a,
                R.styleable.KwKeyboard_horizontalGap,
                parent.mDisplayWidth,
                parent.mDefaultHorizontalGap)
            a.recycle()
        }
    }

    /**
     * Class for describing the position and characteristics of a single key in the keyboard.
     *
     * @attr ref android.R.styleable#Keyboard_keyWidth
     * @attr ref android.R.styleable#Keyboard_keyHeight
     * @attr ref android.R.styleable#Keyboard_horizontalGap
     * @attr ref android.R.styleable#Keyboard_Key_codes
     * @attr ref android.R.styleable#Keyboard_Key_keyIcon
     * @attr ref android.R.styleable#Keyboard_Key_keyLabel
     * @attr ref android.R.styleable#Keyboard_Key_isRepeatable
     * @attr ref android.R.styleable#Keyboard_Key_popupKeyboard
     * @attr ref android.R.styleable#Keyboard_Key_popupCharacters
     * @attr ref android.R.styleable#Keyboard_Key_keyEdgeFlags
     */
    class Key(parent: Row) {
        /** Key code that this key generates.  */
        var code = 0

        /** Label to display  */
        var label: CharSequence = ""

        /** First row of letters can also be used for inserting numbers by long pressing them, show those numbers  */
        var topSmallNumber: String = ""

        /** Icon to display instead of a label. Icon takes precedence over a label  */
        var icon: Drawable? = null

        /** Width of the key, not including the gap  */
        var width: Int

        /** Height of the key, not including the gap  */
        var height: Int

        /** The horizontal gap before this key  */
        var gap: Int

        /** X coordinate of the key in the keyboard layout  */
        var x = 0

        /** Y coordinate of the key in the keyboard layout  */
        var y = 0

        /** The current pressed state of this key  */
        var pressed = false

        /** Focused state, used after long pressing a key and swiping to alternative keys  */
        var focused = false

        /** Popup characters showing after long pressing the key  */
        var popupCharacters: CharSequence? = null

        /**
         * Flags that specify the anchoring to edges of the keyboard for detecting touch events that are just out of the boundary of the key.
         * This is a bit mask of [ItemMainKeyboard.EDGE_LEFT], [ItemMainKeyboard.EDGE_RIGHT].
         */
        private var edgeFlags = 0

        /** The keyboard that this key belongs to  */
        private val keyboard = parent.parent

        /** If this key pops up a mini keyboard, this is the resource id for the XML layout for that keyboard.  */
        var popupResId = 0

        /** Whether this key repeats itself when held down  */
        var repeatable = false

        /** Create a key with the given top-left coordinate and extract its attributes from the XML parser.
         * @param res resources associated with the caller's context
         * @param parent the row that this key belongs to. The row must already be attached to a [ItemMainKeyboard].
         * @param x the x coordinate of the top-left
         * @param y the y coordinate of the top-left
         * @param parser the XML parser containing the attributes for this key
         */
        constructor(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser?) : this(
            parent) {
            this.x = x
            this.y = y
            var a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.KwKeyboard)
            width = getDimensionOrFraction(a,
                R.styleable.KwKeyboard_keyWidth,
                keyboard.mDisplayWidth,
                parent.defaultWidth)
            height = parent.defaultHeight - 10
            gap = getDimensionOrFraction(a,
                R.styleable.KwKeyboard_horizontalGap,
                keyboard.mDisplayWidth,
                parent.defaultHorizontalGap)
            this.x += gap
            a.recycle()
            a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.KwKeyboard_Key)
            code = a.getInt(R.styleable.KwKeyboard_Key_code, 0)

            popupCharacters = a.getText(R.styleable.KwKeyboard_Key_popupCharacters)
            popupResId = a.getResourceId(R.styleable.KwKeyboard_Key_popupKeyboard, 0)
            repeatable = a.getBoolean(R.styleable.KwKeyboard_Key_isRepeatable, false)
            edgeFlags = a.getInt(R.styleable.KwKeyboard_Key_keyEdgeFlags, 0)
            icon = a.getDrawable(R.styleable.KwKeyboard_Key_keyIcon)
            icon?.setBounds(0, 0, icon!!.intrinsicWidth, icon!!.intrinsicHeight)

            label = a.getText(R.styleable.KwKeyboard_Key_keyLabel) ?: ""
            topSmallNumber = a.getString(R.styleable.KwKeyboard_Key_topSmallNumber) ?: ""

            if (label.isNotEmpty() && code != KEYCODE_MODE_CHANGE && code != KEYCODE_SHIFT) {
                code = label[0].code
            }
            a.recycle()
        }

        /** Create an empty key with no attributes.  */
        init {
            height = parent.defaultHeight
            width = parent.defaultWidth
            gap = parent.defaultHorizontalGap
        }

        /**
         * Detects if a point falls inside this key.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return whether or not the point falls inside the key. If the key is attached to an edge, it will assume that all points between the key and
         * the edge are considered to be inside the key.
         */
        fun isInside(x: Int, y: Int): Boolean {
            val leftEdge = edgeFlags and EDGE_LEFT > 0
            val rightEdge = edgeFlags and EDGE_RIGHT > 0
            return ((x >= this.x || leftEdge && x <= this.x + width)
                    && (x < this.x + width || rightEdge && x >= this.x)
                    && (y >= this.y && y <= this.y + height)
                    && (y < this.y + height && y >= this.y))
        }
    }

    /**
     * Creates a keyboard from the given xml key layout file. Weeds out rows that have a keyboard mode defined but don't match the specified mode.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param enterKeyType determines what icon should we show on Enter key
     */
    @JvmOverloads
    constructor(context: Context, @XmlRes xmlLayoutResId: Int, enterKeyType: Int) {
        mDisplayWidth = context.resources.displayMetrics.widthPixels
        mDefaultHorizontalGap = 0
        mDefaultWidth = mDisplayWidth / 10
        mDefaultHeight = mDefaultWidth
        mKeyboardHeightMultiplier = getKeyboardHeightMultiplier()
        mKeys = ArrayList()
        mEnterKeyType = enterKeyType
        loadKeyboard(context, context.resources.getXml(xmlLayoutResId))
    }

    /**
     * Creates a blank keyboard from the given resource file and populates it with the specified characters in left-to-right, top-to-bottom fashion,
     * using the specified number of columns. If the specified number of columns is -1, then the keyboard will fit as many keys as possible in each row.
     * @param context the application or service context
     * @param layoutTemplateResId the layout template file, containing no keys.
     * @param characters the list of characters to display on the keyboard. One key will be created for each character.
     * @param keyWidth the width of the popup key, make sure it is the same as the key itself
     */
    constructor(
        context: Context,
        layoutTemplateResId: Int,
        characters: CharSequence,
        keyWidth: Int,
    ) :
            this(context, layoutTemplateResId, 0) {
        var x = 0
        var y = 0
        var column = 0
        mMinWidth = 0
        val row = Row(this)
        row.defaultHeight = mDefaultHeight
        row.defaultWidth = keyWidth
        row.defaultHorizontalGap = mDefaultHorizontalGap
        mKeyboardHeightMultiplier = getKeyboardHeightMultiplier()

        characters.forEachIndexed { _, character ->
            val key = Key(row)
            if (column >= MAX_KEYS_PER_MINI_ROW) {
                column = 0
                x = 0
                y += mDefaultHeight
                mRows.add(row)
                row.mKeys.clear()
            }

            key.x = x
            key.y = y
            key.label = character.toString()
            key.code = character.code
            column++
            x += key.width + key.gap
            mKeys!!.add(key)
            row.mKeys.add(key)
            if (x > mMinWidth) {
                mMinWidth = x
            }
        }
        mHeight = y + mDefaultHeight
        mRows.add(row)
    }

    fun setShifted(shiftState: Int): Boolean {
        if (this.mShiftState != shiftState) {
            this.mShiftState = shiftState
            return true
        }

        return false
    }

    private fun createRowFromXml(res: Resources, parser: XmlResourceParser?): Row {
        return Row(res, this, parser)
    }

    private fun createKeyFromXml(
        res: Resources,
        parent: Row,
        x: Int,
        y: Int,
        parser: XmlResourceParser?,
    ): Key {
        return Key(res, parent, x, y, parser)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        var inKey = false
        var inRow = false
        var row = 0
        var x = 0
        var y = 0
        var key: Key? = null
        var currentRow: Row? = null
        val res = context.resources
        try {
            var event: Int
            while (parser.next().also { event = it } != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG) {
                    when (parser.name) {
                        TAG_ROW -> {
                            inRow = true
                            x = 0
                            currentRow = createRowFromXml(res, parser)
                            mRows.add(currentRow)
                        }
                        TAG_KEY -> {
                            inKey = true
                            key = createKeyFromXml(res, currentRow!!, x, y + 5, parser)
                            mKeys!!.add(key)
                            if (key.code == KEYCODE_ENTER) {
                                val enterResourceId = when (mEnterKeyType) {
                                    EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_keyboard_search
                                    EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_GO -> R.drawable.ic_keyboard_arrow_right
                                    EditorInfo.IME_ACTION_SEND -> R.drawable.ic_keyboard_send
                                    else -> R.drawable.ic_keyboard_enter
                                }
                                key.icon =
                                    context.resources.getDrawable(enterResourceId, context.theme)
                            }
                            currentRow.mKeys.add(key)
                        }
                        TAG_KEYBOARD -> {
                            parseKeyboardAttributes(res, parser)
                        }
                    }
                } else if (event == XmlResourceParser.END_TAG) {
                    if (inKey) {
                        inKey = false
                        x += key!!.gap + key.width
                        if (x > mMinWidth) {
                            mMinWidth = x
                        }
                    } else if (inRow) {
                        inRow = false
                        y += currentRow!!.defaultHeight
                        row++
                    }
                }
            }
        } catch (e: Exception) {
        }
        mHeight = y
    }

    private fun parseKeyboardAttributes(res: Resources, parser: XmlResourceParser) {
        val a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.KwKeyboard)
        mDefaultWidth = getDimensionOrFraction(a, R.styleable.KwKeyboard_keyWidth, mDisplayWidth, mDisplayWidth / 10)
        mDefaultHeight = res.getDimension(R.dimen.key_height).toInt()
        mDefaultHorizontalGap = getDimensionOrFraction(a, R.styleable.KwKeyboard_horizontalGap, mDisplayWidth, 0)
        a.recycle()
    }

    private fun getKeyboardHeightMultiplier(): Float {
        return 1F
    }
}
