package com.mkleimann.querscraper.service.impl

import com.mkleimann.querscraper.service.QueryService
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery

@ApplicationScoped
class QueryServiceImpl : QueryService {

    @Inject
    lateinit var entityManager: EntityManager

    override fun <T> getNamedQuery(
        name: String,
        resultClass: Class<T>,
        params: Map<String, Any?>,
        page: Int?,
        size: Int?
    ): TypedQuery<T> {
        val query = entityManager.createNamedQuery(name, resultClass)
            ?: throw IllegalArgumentException("NamedQuery '$name' not found!")

        params.entries.forEach {
            query.setParameter(it.key, it.value)
        }

        if (size != null) {
            query.maxResults = size
            if (page != null) {
                query.firstResult = page * size
            }
        }

        return query
    }

    override fun getNamedQuery(name: String, params: Map<String, Any?>, page: Int?, size: Int?) =
        getNamedQuery(name, Array<Any?>::class.javaObjectType, params, page, size)

    override fun getNamedQuery(name: String, page: Int?, size: Int?) =
        getNamedQuery(name, emptyMap(), page, size)

    override fun createCriteriaQuery() =
        createCriteriaQuery(Array<Any?>::class.javaObjectType)

    override fun getCriteriaBuilder(): CriteriaBuilder =
        entityManager.criteriaBuilder

    override fun <T> createCriteriaQuery(resultClass: Class<T>) =
        entityManager.criteriaBuilder.createQuery(resultClass)!!

    override fun <T> buildCriteriaQuery(cq: CriteriaQuery<T>): TypedQuery<T> =
        entityManager.createQuery(cq)

}