@file:JsModule("react-window")
@file:JsNonModule

package bindings

import react.CSSProperties
import react.FC
import react.PropsWithChildren

external interface FixedSizeGridItemParams {
    var columnIndex: Int
    var rowIndex: Int
    var style: CSSProperties
}

external interface FixedSizeGridProps: PropsWithChildren {
    override var children: dynamic
    var columnCount: Int
    var columnWidth: Int
    var height: Int
    var rowCount: Int
    var rowHeight: Int
    var style: dynamic
    var width: Int
}

@JsName("FixedSizeGrid")
external val FixedSizeGrid: FC<FixedSizeGridProps>

external interface FixedSizeListItemParams {
    var index: Int
    var style: CSSProperties
}

external interface FixedSizeListProps: PropsWithChildren {
    override var children: dynamic
    var height: Int
    var itemCount: Int
    var itemSize: Int
    var style: dynamic
    var width: Int
}

@JsName("FixedSizeList")
external val FixedSizeList: FC<FixedSizeListProps>
