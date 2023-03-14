package com.mkleimann.querscraper.service

import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery

/**
 * Encapsulates creation of named queries and criteria queries.
 */
interface QueryService {

    /**
     * Creates a typed query from a named query with a generic type. The query names are found in [QueryNames].
     *
     * @param name the name of the NamedQuery (use [QueryNames] constants)
     * @param resultClass the expected result class
     * @param params the map of parameters for the named query
     * @param page the requested page
     * @param size the number of results
     */
    fun <T> getNamedQuery(
        name: String,
        resultClass: Class<T>,
        params: Map<String, Any?> = emptyMap(),
        page: Int? = null,
        size: Int? = null
    ): TypedQuery<T>

    /**
     * Creates a typed query from a named query for Array<Any>. The query names are found in [QueryNames].
     *
     * @param name the name of the NamedQuery (use [QueryNames] constants)
     * @param params the map of parameters for the named query
     * @param page the requested page
     * @param size the number of results
     */
    fun getNamedQuery(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        page: Int? = null,
        size: Int? = null
    ): TypedQuery<Array<Any?>>

    /**
     * Creates a typed query from a named query without parameters for Array<Any>. The query names are found in [QueryNames].
     *
     * @param name the name of the NamedQuery (use [QueryNames] constants)
     * @param page the requested page
     * @param size the number of results
     */
    fun getNamedQuery(name: String, page: Int? = null, size: Int? = null): TypedQuery<Array<Any?>>

    /**
     * Creates a new [CriteriaQuery] for the given result class.
     *
     * @param resultClass the result class of the query
     */
    fun <T> createCriteriaQuery(resultClass: Class<T>): CriteriaQuery<T>

    /**
     * Creates a new [CriteriaQuery] for multiselect.
     */
    fun createCriteriaQuery(): CriteriaQuery<Array<Any?>>

    /**
     * Returns the [CriteriaBuilder]
     */
    fun getCriteriaBuilder(): CriteriaBuilder

    /**
     * Compiles the given [CriteriaQuery] to a [TypedQuery].
     *
     * @param cq the complete criteria query
     */
    fun <T> buildCriteriaQuery(cq: CriteriaQuery<T>): TypedQuery<T>
}