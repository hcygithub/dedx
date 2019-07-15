package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.ClassDef
import com.dedx.dex.parser.EncValueParser
import com.dedx.dex.parser.StaticValuesParser
import com.dedx.dex.struct.type.TypeBox
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ClassNode private constructor(val parent: DexNode, val cls: ClassDef, clsData: ClassData?): AccessInfo, AttrNode {

    override val attributes: MutableMap<AttrKey, Any> = HashMap()
    override val accFlags: Int = cls.accessFlags
    private val clsInfo: ClassInfo = ClassInfo.fromDex(parent, cls.typeIndex)
    private val interfaces: Array<TypeBox> = Array(cls.interfaces.size) {
        i -> TypeBox.create(parent.dex.strings()[cls.interfaces[i].toInt()])
    }
    private val methods: List<MethodNode> = addMethods(this, clsData)
    private val fields: List<FieldNode> = addFields(this, cls, clsData)

    companion object : ClassNodeFactory<ClassNode> {

        fun addMethods(parent: ClassNode, clsData: ClassData?): List<MethodNode> {
            if (clsData == null) {
                return Collections.emptyList()
            }
            val mthsCount = clsData.directMethods.size + clsData.virtualMethods.size
            val methods = ArrayList<MethodNode>(mthsCount)
            for (method in clsData.directMethods) {
                methods.add(MethodNode(parent, method, false))
            }
            for (method in clsData.virtualMethods) {
                methods.add(MethodNode(parent, method, true))
            }
            return methods
        }

        fun addFields(parent: ClassNode, cls: ClassDef, clsData: ClassData?): List<FieldNode> {
            if (clsData == null) {
                return Collections.emptyList()
            }
            val fieldsCount = clsData.staticFields.size + clsData.instanceFields.size
            val fields = ArrayList<FieldNode>(fieldsCount)
            for (field in clsData.staticFields) {
                fields.add(FieldNode.create(parent, field))
            }
            loadStaticValues(parent, cls, fields)
            for (field in clsData.instanceFields) {
                fields.add(FieldNode.create(parent, field))
            }
            return fields
        }

        private fun loadStaticValues(parent: ClassNode, cls: ClassDef, staticFields: List<FieldNode>) {
            for (field in staticFields) {
                if (field.isFinal()) {
                    field.setValue(AttrKey.CONST, AttrValue(EncValueParser.ENC_NULL, null))
                }
            }
            val offset = cls.staticValuesOffset
            if (offset == 0) {
                return
            }
            val section = parent.parent.dex.open(offset)
            StaticValuesParser(parent.parent, section).processFields(staticFields)
            ConstStorage.processConstFields(parent, staticFields)
        }

        override fun create(parent: DexNode, cls: ClassDef, clsData: ClassData?) = ClassNode(parent, cls, clsData)
    }
}