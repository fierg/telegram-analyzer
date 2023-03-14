package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.service.CsvReader
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
open class CsvReaderImpl : CsvReader {

    override fun forEachLine(vararg filepaths: String, callback: (Map<String, String?>) -> Unit) {
        for (file in filepaths) {
            val path = "masterdata/$file"

            var idx = 0
            val header = mutableListOf<String>()

            val br =
                BufferedReader(
                    InputStreamReader(
                        CsvReaderImpl::class.java.classLoader.getResourceAsStream(path)!!,
                        "UTF-8"
                    )
                )
            br.use { reader ->
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {

                        val values = line.split(Regex(";(?=([^\"]*\"[^\"]*\")*[^\"]*\$)"))
                        if (idx < 1) {
                            header.addAll(values)
                        } else {
                            callback(values.mapIndexed { idx, it -> header[idx] to it.ifBlank { null } }
                                .toMap())
                        }

                        idx++
                    }
                }
            }
        }
    }
}