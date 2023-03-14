package com.mkleimann.querscraper.repo

import com.mkleimann.querscraper.model.entity.GraphEdge
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class GraphEdgeRepository : PanacheRepositoryBase<GraphEdge, String>