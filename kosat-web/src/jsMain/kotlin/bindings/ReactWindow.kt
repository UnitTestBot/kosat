@file:JsModule("react-window")
@file:JsNonModule

package bindings

import react.CSSProperties
import react.FC
import react.Props
import react.ReactNode
import react.Ref
import web.html.HTMLElement

// https://github.com/bvaughn/react-window
// Since rendering of thousands of DOM nodes can be slow (and we have to render
// quire a few long lists), we use react-window to render only the visible
// elements of a list. This comes with a limitation that we must use absolute
// sizes for the elements, which ruins the responsiveness, but it's a tradeoff
// we're willing to make to render bigger problems.

// This is bindings for that library.


/**
 * Used for [FixedSizeGrid].
 *
 * Each element in the grid is rendered by calling [FixedSizeGridProps.children]
 * as a function. Parameters to that function are passed in this interface.
 */
external interface FixedSizeGridItemParams {
    var columnIndex: Int
    var rowIndex: Int
    var style: CSSProperties
}

/**
 * Props for [FixedSizeGrid].
 */
external interface FixedSizeGridProps : Props {
    /**
     * Represents the function that renders each element in the grid.
     */
    var children: (FixedSizeGridItemParams) -> ReactNode

    /**
     * Number of columns in the grid.
     */
    var columnCount: Int

    /**
     * Width of each column in the grid.
     */
    var columnWidth: Int

    /**
     * Height of the visible part of the grid.
     */
    var height: Int

    /**
     * Number of rows in the grid.
     */
    var rowCount: Int

    /**
     * Height of each row in the grid.
     */
    var rowHeight: Int

    /**
     * Additional styles to apply to the grid.
     */
    var style: CSSProperties

    /**
     * Width of the visible part of the grid.
     */
    var width: Int
}

/**
 * A grid with fixed size elements.
 *
 * https://react-window.vercel.app/#/api/FixedSizeGrid
 */
@JsName("FixedSizeGrid")
external val FixedSizeGrid: FC<FixedSizeGridProps>

/**
 * Used for [FixedSizeList].
 *
 * Each element in the list is rendered by calling [FixedSizeListProps.children]
 * as a function. Parameters to that function are passed in this interface.
 */
external interface FixedSizeListItemParams {
    var index: Int
    var style: CSSProperties
}

/**
 * Props for [FixedSizeList].
 */
external interface FixedSizeListProps : Props {
    /**
     * Represents the function that renders each element in the list.
     */
    var children: (FixedSizeListItemParams) -> ReactNode

    /**
     * Height of the visible part of the list.
     */
    var height: Int

    /**
     * Number of elements in the list.
     */
    var itemCount: Int

    /**
     * Height of each element in the list.
     */
    var itemSize: Int

    /**
     * Additional styles to apply to the list.
     */
    var style: CSSProperties

    /**
     * Width of the visible part of the list.
     */
    var width: Int

    var outerRef: Ref<HTMLElement>
}

/**
 * A list with fixed size elements.
 *
 * https://react-window.vercel.app/#/api/FixedSizeList
 */
@JsName("FixedSizeList")
external val FixedSizeList: FC<FixedSizeListProps>
