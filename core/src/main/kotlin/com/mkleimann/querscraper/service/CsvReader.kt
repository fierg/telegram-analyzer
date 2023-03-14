package com.mkleimann.querscraper.service

/**
 * The [CsvReader] is responsible for reading CSV files
 */
interface CsvReader {

    /**
     * Streams the CSV file and passes the map of values into the callback function for each row.
     *
     * The CSV file must have the following format:
     * - UTF 8 encoding
     * - seperated by semicolon (;)
     * - first row: header
     * - second to n: data
     *
     * @param filepath the file path relative to 'src/main/resources/masterdata'
     * @param func the callback function
     */
    fun forEachLine(vararg filepaths: String, callback: (Map<String, String?>) -> Unit)
}